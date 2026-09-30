/**
 * <PRE>
 * 
 * Copyright Tony Bringarder 1998, 2025 <A href="http://bringardner.com/tony">Tony Bringardner</A>
 * 
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       <A href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</A>
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  </PRE>
 *   
 *   
 *	@author Tony Bringardner   
 *
 *
 * ~version~V000.01.48-V000.01.39-V000.01.37-V000.01.35-V000.01.28-V000.01.26-V000.01.25-V000.01.24-V000.01.19-V000.01.14-V000.01.13-V000.01.07-V000.01.05-V000.01.03-V000.00.03-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.net.ftp.server;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.Properties;

import javax.net.ssl.SSLContext;

import us.bringardner.core.ILogger;
import us.bringardner.core.ILogger.Level;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.net.framework.Connection;
import us.bringardner.net.framework.IConnection;
import us.bringardner.net.framework.IConnectionFactory;
import us.bringardner.net.framework.IProcessor;
import us.bringardner.net.framework.IProcessorFactory;
import us.bringardner.net.framework.server.AbstractPrincipal;
import us.bringardner.net.framework.server.IAccessControlList;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IPrincipal;
import us.bringardner.net.framework.server.IServer;
import us.bringardner.net.framework.server.Server;


/**
 * @author Tony Bringardner
 *
 */
public class FtpServer extends Server {

	public static final int FTP_PORT = 21;
	public static final int FTP_SSL_PORT = 421;
	public static final String FTP_NAME = "JFtp";
	
	
	public static final String CONFIG_PROP = FTP_NAME+".properties";	
	public static final String ROOT_PROP = FTP_NAME+".root";
	public static final String DEBUG_PROP = FTP_NAME+".debug";
	public static final String FILE_SOURCE_PROP = FTP_NAME+".fileSource";
    
	public static final String DEFAULT_ROOT_WINDOWS = "C:/ftp";
	public static final String DEFAULT_ROOT = "/ftp";
	public static final String EXTERNAL_ADDRESS_PROP = FTP_NAME+".externalAddress";
	public static final String PASIVE_CONTROL_PORT_PROP = FTP_NAME+".controlPort";
	public static final String PASIVE_CONTROL_MIN_PROP = FTP_NAME+".minControlPort";
	public static final String PASIVE_CONTROL_MAX_PROP = FTP_NAME+".maxControlPort";
	
	/** Default size (bytes) of the buffer used to copy data-channel transfers. */
	public static final int DEFAULT_BUFFER_SIZE = 64 * 1024;
	public static final String BUFFER_SIZE_PROP = FTP_NAME+".bufferSize";
	/** Default data-connection inactivity timeout (ms) */
	public static final int DEFAULT_DATA_TIMEOUT = 10 * 60 * 1000;
	public static final String DATA_TIMEOUT_PROP = FTP_NAME+".dataTimeout";
	/** Default timeout (ms) for connecting to the client in active mode (PORT/EPRT) */
	public static final int DEFAULT_CONNECT_TIMEOUT = 15 * 1000;
	public static final String CONNECT_TIMEOUT_PROP = FTP_NAME+".connectTimeout";
	/**
	 * If "true", PORT/EPRT may name any address/port and PASV accepts a data connection from any
	 * address (needed for FXP / server-to-server transfers). Default false (RFC 2577 protections).
	 */
	public static final String ALLOW_FOREIGN_DATA_ADDRESS_PROP = FTP_NAME+".allowForeignDataAddress";
	/**
	 * How symbolic links inside a user's root are treated. {@code ..} can never leave the
	 * root in any mode (it is resolved on the virtual path first).
	 */
	public enum SymlinkPolicy {
		/** Links that lead outside the root are refused (default). */
		STRICT,
		/** Links may lead outside the root only into the directories listed in allowedLinkTargets. */
		ALLOWED_TARGETS,
		/** Every link inside the root is followed, wherever it points (only when users can't create links). */
		FOLLOW;

		/** Parse "strict", "allowedTargets" / "allowed_targets" or "follow" (case-insensitive). */
		public static SymlinkPolicy parse(String value) {
			String v = value.trim().replace("_", "").replace("-", "").toLowerCase(java.util.Locale.ROOT);
			switch (v) {
			case "strict": return STRICT;
			case "allowedtargets": return ALLOWED_TARGETS;
			case "follow": return FOLLOW;
			default: throw new IllegalArgumentException("Invalid symlink policy '"+value+"' (use strict, allowedTargets or follow)");
			}
		}
	}
	public static final String SYMLINK_POLICY_PROP = FTP_NAME+".symlinkPolicy";
	/** Directories that links may lead into with SymlinkPolicy.ALLOWED_TARGETS, separated by commas. */
	public static final String ALLOWED_LINK_TARGETS_PROP = FTP_NAME+".allowedLinkTargets";

	/** Delay (ms) before replying to a failed login, to slow down password guessing. */
	public static final int DEFAULT_LOGIN_FAILURE_DELAY = 1000;
	public static final String LOGIN_FAILURE_DELAY_PROP = FTP_NAME+".loginFailureDelay";

	private volatile int bufferSize = Integer.getInteger(BUFFER_SIZE_PROP, DEFAULT_BUFFER_SIZE);
	private volatile int dataTimeout = Integer.getInteger(DATA_TIMEOUT_PROP, DEFAULT_DATA_TIMEOUT);
	private volatile int connectTimeout = Integer.getInteger(CONNECT_TIMEOUT_PROP, DEFAULT_CONNECT_TIMEOUT);
	private volatile boolean allowForeignDataAddress = Boolean.getBoolean(ALLOW_FOREIGN_DATA_ADDRESS_PROP);
	private volatile int loginFailureDelay = Integer.getInteger(LOGIN_FAILURE_DELAY_PROP, DEFAULT_LOGIN_FAILURE_DELAY);
	private volatile SymlinkPolicy symlinkPolicy = SymlinkPolicy.parse(System.getProperty(SYMLINK_POLICY_PROP, "strict"));
	private volatile java.util.List<String> allowedLinkTargets = parseTargets(System.getProperty(ALLOWED_LINK_TARGETS_PROP, ""));
	private FileSource ftpRoot;
	//private boolean useJdbc = false;
	private FileSourceFactory factory = FileSourceFactory.getDefaultFactory();
	
	private class ServerConnection extends Connection {

		public ServerConnection( Socket socket, boolean useCRLF,Level logLevel) throws IOException {
			super(socket,useCRLF); 
			getLogger().setLevel(logLevel);
		}

		@Override
		public SSLContext getSSLContext(String sslOrTsl) throws IOException {
			// Server.getSSLContext(String) builds the context under a lock. The old code
			// swapped the server's shared protocol field with no lock, so two clients
			// running AUTH at the same time could interfere with each other.
			return FtpServer.this.getSSLContext(sslOrTsl);
		}
		
	}
	
	/**
	 * @param port
	 * @param name
	 * @param secure
	 * @throws IOException 
	 */
	public FtpServer(int port, String name,  boolean secure) {
		super(port, name);
		setPropertyPrefix("FtpServer");
		setSecure(secure);
		setDaemon(false);
		initMe();
		// INFO by default: DEBUG logs several lines per transfer, including paths and client addresses
		getLogger().setLevel(us.bringardner.core.ILogger.Level.INFO);
	}
	
	public FtpServer() {
		this(FTP_PORT,FTP_NAME,false);
	}
	
	public FtpServer(boolean secure) {
		this(FTP_PORT,FTP_NAME,secure);
	}
	
	public FtpServer(FileSource root, boolean secure) {
		this(FTP_PORT,FTP_NAME,secure);
		// root was previously ignored
		try {
			setFtpRoot(root);
		} catch (IOException e) {
			throw new java.io.UncheckedIOException("Invalid FTP root "+root, e);
		}
	}

	public static void main(String[] args) throws Exception {
		System.out.println("\nStarting FtpServer with "+args.length+" args");
		for(int idx = 0; idx < args.length; idx++ ) {
			if( args[idx].startsWith("-D")) {
				String [] tmp = args[idx].substring(2).split("=");
				if( tmp.length == 2) {
					System.out.println("\t"+tmp[0]+"="+tmp[1]);
					System.setProperty(tmp[0],tmp[1]);
				} else {
					System.out.println("Invalid arg = "+args[idx]);
				}
			} else {
				System.out.println("\t"+args[idx]+"="+args[idx+1]);
				System.setProperty(args[idx++],args[idx]);
			}
		}
		
		String tmp = System.getProperty(CONFIG_PROP);
		if( tmp != null ) {
			System.out.println("Looking for "+tmp);
			File file = new File(tmp);
			InputStream in = new FileInputStream(file);
			Properties prop = System.getProperties();
			
			try {
				prop.load(in);
			} finally {
				in.close();
			}
			
			System.out.println("Loaded properties from "+tmp);
			//  Anything on the command line should override the properties file
			for(int idx = 0; idx < args.length; idx++ ) {
				System.setProperty(args[idx++],args[idx]);
			}
		}
		int port = FTP_PORT;
		tmp = System.getProperty(FTP_NAME+".port");
		if( tmp != null )  {
			port = Integer.parseInt(tmp);
		}
		
		boolean sucure = System.getProperty(FTP_NAME+".secure", "false").toLowerCase().equals("true");
		FtpServer server = new FtpServer(port, FTP_NAME,sucure);		
		server.start();
		System.out.println("FtpServer started on port "+port);
		
	}

	private void initMe()  {
		
		setName("FtpServer");
		
		setProcessorFactory(new IProcessorFactory() {
			public IProcessor getProcessor() {
				FtpRequestProcessor ret = new FtpRequestProcessor();
				ret.getLogger().setLevel(FtpServer.this.getLogger().getLevel());
				ret.setActivityTimeOut(getDataTimeout());
				return ret;
			}			
		});
		
		setConnectionFactory(new IConnectionFactory() {
			public IConnection getConnection(Socket socket) throws IOException {
				return new ServerConnection(socket,true,FtpServer.this.getLogger().getLevel());
			}			
		});
		
		//  Determine which FileSource to use
		String tmp = System.getProperty(FILE_SOURCE_PROP);
		if( tmp != null ) {
			tmp = tmp.toLowerCase();
			setFileSourceFactory(FileSourceFactory.getFileSourceFactory(tmp));
		} else {
			setFileSourceFactory(FileSourceFactory.getDefaultFactory());
		}
		
		
		tmp = System.getProperty(ROOT_PROP);
		if( tmp == null ) {

			if(System.getProperty("os.name").toLowerCase().indexOf("win") >= 0) {
				tmp = DEFAULT_ROOT_WINDOWS;
			} else {
				tmp = DEFAULT_ROOT;
			}
	 
		}
		
		try {
			setFtpRoot(factory.createFileSource(tmp));
		} catch (IOException e) {
			logInfo("Error attmtping set set root "+tmp+" using "+factory.getTypeId()+" factory");
		}
		
		
		if( (tmp = System.getProperty(PASIVE_CONTROL_PORT_PROP)) != null ) {
			try {
				PassiveSocket.setControlPort(Integer.parseInt(tmp));
			} catch (Exception e) {
				throw new IllegalArgumentException("Can't configure passive control port='"+tmp+"'",e);
			}
		}

		
		if( (tmp = System.getProperty(PASIVE_CONTROL_MIN_PROP)) != null ) {
			try {
				PassiveSocket.setMinControlPort(Integer.parseInt(tmp));
			} catch (Exception e) {
				throw new IllegalArgumentException("Can't configure passive min control port='"+tmp+"'",e);
			}
		}
		
		if( (tmp = System.getProperty(PASIVE_CONTROL_MAX_PROP)) != null ) {
			try {
				PassiveSocket.setMaxControlPort(Integer.parseInt(tmp));
			} catch (Exception e) {
				throw new IllegalArgumentException("Can't configure passive max control port='"+tmp+"'",e);
			}
		}
		//  trigger access control initialization 
		IAccessControlList acl = getAccessControl();
		if( acl == null ) {
			//  I'll honor the RFC 959 for ANONYMOUS and FTP users
			setAccessControl(new IAccessControlList() {
				
				IPrincipal anonymous = new AbstractPrincipal("anonymous") {

					@Override
					public boolean authenticate(byte[] credentials) {
						return true;
					}					
				} ;

				@Override
				public void initialize(IServer server) throws IOException {
					anonymous.setParameter(FtpRequestProcessor.PARAMETER_ROOT, "/anonymous");
				}
				
				@Override
				public IPrincipal getPrincipal(String user) {
					if( !(user.equalsIgnoreCase("anonymous") || user.equalsIgnoreCase("ftp"))) {
						return null;
					}
					
					return anonymous;						
					
				}
				
				@Override
				public boolean checkPermission(IPrincipal user, IPermission action) {
					// give anonymous all but admin rights
					boolean ret = user.getName().equals("anonymous") && !FtpCommand.ADMIN_PERMISSION.equals(action);
					
					return ret;
				}
			});
		}
		
	}
	
	
	public FileSource getFtpRoot() throws IOException {
		if( ftpRoot == null ) {
			// e.g. the configured root could not be created
			throw new IOException("The FTP root is not configured (see "+ROOT_PROP+")");
		}
		if( !ftpRoot.exists()){
			ftpRoot.mkdirs();
		}
		return ftpRoot;
	}
	
	public void setFtpRoot(FileSource ftpRoot) throws IOException {
		if( !ftpRoot.exists() ){
			ftpRoot.mkdirs();
		} else if( !ftpRoot.isDirectory() ) {
			throw new RuntimeException("Invalid root dir = "+ftpRoot);
		}
		this.ftpRoot = ftpRoot;
		setFileSourceFactory(ftpRoot.getFileSourceFactory());
	}
	
	/**
	 * @return size (bytes) of the buffer used for data-channel transfers.
	 */
	public int getBufferSize() {
		return bufferSize;
	}

	public void setBufferSize(int bufferSize) {
		if( bufferSize <= 0 ) {
			throw new IllegalArgumentException("bufferSize must be > 0");
		}
		this.bufferSize = bufferSize;
	}

	/**
	 * @return the data-connection inactivity timeout in milliseconds. A transfer
	 * that moves no data for this long is aborted with a 426 reply.
	 */
	public int getDataTimeout() {
		return dataTimeout;
	}

	/**
	 * Applies to processors (connections) created after this call.
	 * @param dataTimeout inactivity timeout in milliseconds (must be > 0)
	 */
	public void setDataTimeout(int dataTimeout) {
		if( dataTimeout <= 0 ) {
			throw new IllegalArgumentException("dataTimeout must be > 0");
		}
		this.dataTimeout = dataTimeout;
	}

	/**
	 * @return timeout (ms) used when connecting to the client for an active (PORT/EPRT) data connection.
	 */
	public int getConnectTimeout() {
		return connectTimeout;
	}

	public void setConnectTimeout(int connectTimeout) {
		if( connectTimeout <= 0 ) {
			throw new IllegalArgumentException("connectTimeout must be > 0");
		}
		this.connectTimeout = connectTimeout;
	}

	/**
	 * @return true if data connections may use an address other than the client's
	 * control connection address (FXP). Default false.
	 */
	public boolean isAllowForeignDataAddress() {
		return allowForeignDataAddress;
	}

	/**
	 * RFC 2577: by default PORT/EPRT must name the client's own address and a port >= 1024,
	 * and a passive data connection must come from the client's address. This prevents
	 * "FTP bounce" attacks and data connection theft. Set true only for FXP.
	 */
	public void setAllowForeignDataAddress(boolean allow) {
		this.allowForeignDataAddress = allow;
	}

	/**
	 * @return the delay (ms) before replying to a failed PASS/ACCT.
	 */
	public int getLoginFailureDelay() {
		return loginFailureDelay;
	}

	/**
	 * @param loginFailureDelay delay (ms) before replying to a failed PASS/ACCT (0 = none).
	 * Combined with the 3 attempts per connection limit this slows down password guessing.
	 */
	public void setLoginFailureDelay(int loginFailureDelay) {
		if( loginFailureDelay < 0 ) {
			throw new IllegalArgumentException("loginFailureDelay must be >= 0");
		}
		this.loginFailureDelay = loginFailureDelay;
	}

	/**
	 * @return how symbolic links inside a user's root are treated (default STRICT).
	 */
	public SymlinkPolicy getSymlinkPolicy() {
		return symlinkPolicy;
	}

	/**
	 * @param policy STRICT (default): links out of the root are refused;
	 * ALLOWED_TARGETS: links may lead into {@link #setAllowedLinkTargets(java.util.List)};
	 * FOLLOW: every link in the root is followed. Takes effect on the next command.
	 */
	public void setSymlinkPolicy(SymlinkPolicy policy) {
		if( policy == null ) {
			throw new IllegalArgumentException("policy must not be null");
		}
		this.symlinkPolicy = policy;
	}

	/**
	 * @return the directories links may lead into with SymlinkPolicy.ALLOWED_TARGETS.
	 */
	public java.util.List<String> getAllowedLinkTargets() {
		return allowedLinkTargets;
	}

	/**
	 * @param targets directories (paths in the server's file system) that symbolic links
	 * inside a user's root may lead into when the policy is ALLOWED_TARGETS.
	 */
	public void setAllowedLinkTargets(java.util.List<String> targets) {
		java.util.List<String> copy = new java.util.ArrayList<String>();
		if( targets != null ) {
			for(String t : targets) {
				if( t != null && !t.trim().isEmpty() ) {
					copy.add(t.trim());
				}
			}
		}
		this.allowedLinkTargets = java.util.Collections.unmodifiableList(copy);
	}

	private static java.util.List<String> parseTargets(String value) {
		java.util.List<String> ret = new java.util.ArrayList<String>();
		for(String t : value.split(",")) {
			if( !t.trim().isEmpty() ) {
				ret.add(t.trim());
			}
		}
		return java.util.Collections.unmodifiableList(ret);
	}

	public FileSourceFactory getFileSourceFactory() {
		return factory;
	}
	
	public void setFileSourceFactory(FileSourceFactory factory) {
		this.factory = factory;
	}
	
	@Override
	public void logDebug(String msg) {
		if( isSecure()) {
			msg = "(S) "+msg;
		}
		super.logDebug(msg);
	}

	@Override
	public void logDebug(String msg, Throwable error) {
		if( isSecure()) {
			msg = "(S) "+msg;
		}
		
		super.logDebug(msg, error);
	}

	@Override
	protected ILogger getLogger(String name) {
		return super.getLogger("FtpServer");
	}
}
