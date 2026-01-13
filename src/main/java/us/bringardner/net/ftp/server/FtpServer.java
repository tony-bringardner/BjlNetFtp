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
			String tmp = FtpServer.this.getProtocol();
			FtpServer.this.setProtocol(sslOrTsl);
			SSLContext ret = FtpServer.this.getSSLContext();
			FtpServer.this.setProtocol(tmp);
			return ret;
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
		getLogger().setLevel(us.bringardner.core.ILogger.Level.DEBUG);
	}
	
	public FtpServer() {
		this(FTP_PORT,FTP_NAME,false);
	}
	
	public FtpServer(boolean secure) {
		this(FTP_PORT,FTP_NAME,secure);
	}
	
	public FtpServer(FileSource root, boolean secure) {
		this(FTP_PORT,FTP_NAME,secure);
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
				logError("Can't configure passive control port='"+tmp+"'",e);
				System.exit(1);
			}
		}

		
		if( (tmp = System.getProperty(PASIVE_CONTROL_MIN_PROP)) != null ) {
			try {
				PassiveSocket.setMinControlPort(Integer.parseInt(tmp));
			} catch (Exception e) {
				logError("Can't configure passive min control port='"+tmp+"'",e);
				System.exit(1);
			}
		}
		
		if( (tmp = System.getProperty(PASIVE_CONTROL_PORT_PROP)) != null ) {
			try {
				PassiveSocket.setMaxControlPort(Integer.parseInt(tmp));
			} catch (Exception e) {
				logError("Can't configure passive max control port='"+tmp+"'",e);
				System.exit(1);
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
