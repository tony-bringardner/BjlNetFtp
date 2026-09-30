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
 * ~version~V000.01.56-V000.01.55-V000.01.52-V000.01.51-V000.01.50-V000.01.48-V000.01.46-V000.01.43-V000.01.42-V000.01.40-V000.01.36-V000.01.35-V000.01.33-V000.01.18-V000.01.16-V000.01.15-V000.01.13-V000.01.12-V000.01.11-V000.01.09-V000.01.05-V000.01.03-V000.01.02-V000.01.02-V000.00.03-V000.00.01-V000.00.00-
 */
/*
 * Created on Nov 24, 2006
 *
 */
package us.bringardner.net.ftp.client;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import us.bringardner.core.SecureBaseObject;
import us.bringardner.io.CRLFLineReader;
import us.bringardner.io.CRLFLineWriter;
import us.bringardner.net.ftp.FTP;
import us.bringardner.net.ftp.server.commands.Site;

/**
 * @author Tony Bringardner
 * This is a VERY limited client but it can be used to build more robust solutions.
 *  
 */
public class FtpClient extends SecureBaseObject implements FTP {

	public enum Permissions {
		OwnerRead('r'),
		OwnerWrite('w'),
		OwnerExecute('x'),

		GroupRead('r'),
		GroupWrite('w'),
		GroupExecute('x'),

		OtherRead('r'),
		OtherWrite('w'),
		OtherExecute('x');

	    public final char label;

	    private Permissions(char label) {
	        this.label = label;
	    }
	}

	public static final char SEPERATOR_CHAR = '/';
	public static final String SEPERATOR = ""+SEPERATOR_CHAR;
	private static final String [] SECURE_TYPES = {"TLS","SSL"};
	private SocketFactory socketFactory;
	private ServerSocketFactory serverSocketFactory = ServerSocketFactory.getDefault();

	private boolean requestSecure = true;
	private boolean requireSecure = false;


	private String host;
	private int port = FTP_PORT;
	private boolean useSsl;
	private volatile Socket socket;
	private volatile SSLSocket sslSocket;
	public StringBuilder dialog = new StringBuilder();
	private volatile CRLFLineReader input;
	private volatile CRLFLineWriter output;
	private boolean connected = false;
	//  Socket timeout in milliseconds
	private int cmdTimeout = 60000;
	//  Socket linger in seconds (off by default, a positive value makes close() block)
	private int cmdLinger = -1;
	private int txferTimeout = 60000;
	private int transferLinger = -1;    
	private volatile String currentDir;
	private ClientFtpResponse lastResponse;

	private volatile String userId;
	private volatile String password;
	private volatile String account;
	private Map<String, String> featResponse;
	private Map<String,AutoCloseable> streamsInProcess = new HashMap<>();
	/** Try EPSV before PASV (RFC 2428). */
	private volatile boolean useEpsv = true;
	/** Set when the server rejected EPSV, so PASV is used for the rest of the session. */
	private volatile boolean epsvRejected = false;
	/** Reconnect (and retry safe commands once) when the server has closed the control connection. */
	private volatile boolean autoReconnect = true;
	/** Set by readLine() when the server closed the control connection. */
	private volatile boolean peerClosed = false;

	/**
	 * Commands that can be sent again on a new connection without changing anything on
	 * the server (after the connection was found to be closed).
	 */
	private static final java.util.Set<String> RETRY_SAFE = new java.util.HashSet<>(java.util.Arrays.asList(
			"NOOP","PWD","XPWD","CWD","CDUP","TYPE","MODE","STRU","PASV","EPSV","SIZE","MDTM",
			"FEAT","OPTS","MLST","SYST","STAT","HELP"));
	




	/**
	 * Manage concurrency <code>socketLock</code>
	 */
	private Object socketLock = new Object();
	/**
	 * Manage concurrency <code>inputLock</code>
	 */
	private Object inputLock = new Object();
	/**
	 * Manage concurrency <code>outputLock</code>
	 */
	private Object outputLock = new Object();

	private volatile boolean mlstTested = false;
	private volatile boolean mlstSupported = false;
	private int transferBufferSize = 1024*65;
	private boolean usePasvAddress = false;
	private boolean active = false;
	private volatile boolean channelSecure;
	/** true if data connections use TLS (PROT P accepted, or a legacy server without PBSZ) */
	private volatile boolean dataChannelSecure;
	private volatile boolean forceList;

	/**
	 * @return true if directory listings always use LIST even when the server supports MLSD.
	 */
	public boolean isForceList() {
		return forceList;
	}

	/**
	 * @param forceList true to always use LIST instead of MLSD for directory listings.
	 */
	public void setForceList(boolean forceList) {
		this.forceList = forceList;
	}


	/**
	 * Create a new FtpClient without specifying a host name. Use setHost instead.
	 */
	public FtpClient() {
		this.host = "Undefined";
	}

	/**
	 * Create a new FtpClient
	 * 
	 * @param host to connect to port is assumed to be 21 
	 * unless modified with the setPort method.
	 */
	public FtpClient(String host) {
		this.host = host;
	}

	/**
	 * @param host The host name to connect to 
	 * @param port The port to connect to (usually 21)
	 */
	public FtpClient(String host, int port) {
		this(host);
		this.port = port;
	}



	public SocketFactory getSocketFactory() throws IOException {
		if( socketFactory == null ) {
			if( isSecure() || isChannelSecure() ) {
				socketFactory = getSSLContext().getSocketFactory();
			} else {
				socketFactory = SocketFactory.getDefault();				
			}
		}
		return socketFactory;
	}

	public void setSocketFactory(SocketFactory socketFactory) {
		this.socketFactory = socketFactory;
	}



	public boolean isChannelSecure() {
		return channelSecure;
	}

	public boolean isUseSsl() {
		return useSsl;
	}

	public void setUseSsl(boolean useSsl) {
		this.useSsl = useSsl;
	}

	public int getTxferTimeout() {
		return txferTimeout;
	}

	public void setTxferTimeout(int txferTimeout) {
		this.txferTimeout = txferTimeout;
	}

	public String getCurrentDir() throws IOException {
		return executePwd();
	}

	public boolean setCurrentDir(String dir) throws IOException {
		return executeCwd(dir);
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public char[] getPermissions(String path) throws IOException {
		char[] ret = "---------".toCharArray();
		String[] resp= executeList(true, path);
		// should be one and only one line
		if( resp!=null && resp.length==1) {
			if( resp[0].length()>=9) {
				ret = resp[0].substring(1,10).toCharArray();
			}
		}
		return ret;
	}

	public int getUnixPermitionValue(char perms []) throws IOException {
		
		int user = ((perms[Permissions.OwnerRead.ordinal()]=='r') ? 4:0)
				| ((perms[Permissions.OwnerWrite.ordinal()]=='w') ? 2:0)
				| ((perms[Permissions.OwnerExecute.ordinal()]=='x') ? 1:0)
				;
		
		int group = ((perms[Permissions.GroupRead.ordinal()]=='r') ? 4:0)
				| ((perms[Permissions.GroupWrite.ordinal()]=='w') ? 2:0)
				| ((perms[Permissions.GroupExecute.ordinal()]=='x') ? 1:0)
				;
		int other = ((perms[Permissions.OtherRead.ordinal()]=='r') ? 4:0)
				| ((perms[Permissions.OtherWrite.ordinal()]=='w') ? 2:0)
				| ((perms[Permissions.OtherExecute.ordinal()]=='x') ? 1:0)
				;
		
		int ret = (user<<6) | (group<<3) | other;
		
		return ret;
	}
	
	public boolean canOwnerRead(String path) throws IOException {
		return getPermissions(path)[Permissions.OwnerRead.ordinal()]!='-';
	}
	public boolean canOwnerWrite(String path) throws IOException {
		return getPermissions(path)[Permissions.OwnerWrite.ordinal()]!='-';
	}
	
	public boolean canOwnerExecute(String path) throws IOException {
		return getPermissions(path)[Permissions.OwnerExecute.ordinal()]!='-';
	}
	
	public boolean canOtherRead(String path) throws IOException {
		return getPermissions(path)[Permissions.OtherRead.ordinal()]!='-';
	}
	public boolean canOtherWrite(String path) throws IOException {
		return getPermissions(path)[Permissions.OtherWrite.ordinal()]!='-';
	}
	
	public boolean canOtherExecute(String path) throws IOException {
		return getPermissions(path)[Permissions.OtherExecute.ordinal()]!='-';
	}
	
	public boolean canGroupRead(String path) throws IOException {
		return getPermissions(path)[Permissions.GroupRead.ordinal()]!='-';
	}
	public boolean canGroupWrite(String path) throws IOException {
		return getPermissions(path)[Permissions.GroupWrite.ordinal()]!='-';
	}
	
	public boolean canGroupExecute(String path) throws IOException {
		return getPermissions(path)[Permissions.GroupExecute.ordinal()]!='-';
	}
	
	public boolean setOwnerReadable(String path,boolean b) throws IOException {
		return setPermission(Permissions.OwnerRead, b, path);
	}
	
	public boolean setOwnerWritable(String path,boolean b) throws IOException {
		return setPermission(Permissions.OwnerWrite, b, path);
	}
	
	public boolean setOwnerExecutable(String path,boolean b) throws IOException {
		return setPermission(Permissions.OwnerExecute, b, path);
	}
	
	
	public boolean setGroupReadable(String path,boolean b) throws IOException {
		return setPermission(Permissions.GroupRead, b, path);
	}
	
	public boolean setGroupWritable(String path,boolean b) throws IOException {
		return setPermission(Permissions.GroupWrite, b, path);
	}

	public boolean setGroupExecutable(String path,boolean b) throws IOException {
		return setPermission(Permissions.GroupExecute, b, path);
	}
	
	public boolean setOtherReadable(String path,boolean b) throws IOException {
		return setPermission(Permissions.OtherRead, b, path);
	}
	
	public boolean setOtherWritable(String path,boolean b) throws IOException {
		return setPermission(Permissions.OtherWrite, b, path);
	}
	
	public boolean setOtherExecutable(String path,boolean b) throws IOException {
		return setPermission(Permissions.OtherExecute, b, path);
	}
	
	private boolean setPermission(Permissions p, boolean b,String path) throws IOException {
		int idx = p.ordinal();
		// get the current permissions
		char perms [] = getPermissions(path);
		char label = b ? p.label:'-';
		boolean ret = perms[idx] == label;
		// nothing to do if it's already set
		if( !ret ) {
			perms[idx] = label;
			int val = getUnixPermitionValue(perms);
			String arg = Integer.toOctalString(val);
			ClientFtpResponse resp = executeCommand(FTP.SITE, Site.CMD_CHMOD,arg,path);
			ret = resp.isPositiveComplet();						
		}
		
		return ret;
	}


	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}


	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public void setTransferBufferSize(int transferBufferSize) {
		this.transferBufferSize = transferBufferSize;
	}

	/**
	 * @return SoLinger value for the Command Socket (in seconds)
	 */
	public int getCmdLinger() {
		return cmdLinger;
	}

	/**
	 * Set the SoLinger value for the Command socket (in seconds)
	 * @param cmdLinger
	 */
	public void setCmdLinger(int cmdLinger) {
		this.cmdLinger = cmdLinger;
	}

	/**
	 * @return SoLinger value for the Transfer Socket (in seconds)
	 */
	public int getTransferLinger() {
		return transferLinger;
	}

	/**
	 * Set the SoLinger value for the Transfer socket (in seconds)
	 * @param transferLinger
	 */
	public void setTransferLinger(int transferLinger) {
		this.transferLinger = transferLinger;
	}

	/**
	 * @return The host name of the FTP Server.
	 */
	public String getHost() {
		return host;
	}


	/**
	 * @param host (The host name of the FTP Server)
	 */
	public void setHost(String host) {
		this.host = host;
	}

	public String getAccount() {
		return account;
	}

	public void setAccount(String account) {
		this.account = account;
	}

	/**
	 * @return The CRLFLineReader assigned to the command channel.
	 * 
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public CRLFLineReader getInput() throws UnknownHostException, IOException {
		if( input == null ) {
			synchronized (inputLock) {
				if(input == null ) {
					input = new CRLFLineReader(getSocket().getInputStream());
				}
			}
		}
		return input;
	}


	/**
	 * @return The CRLFLineWriter assigned to the Command channel.
	 * 
	 * @throws IOException
	 * 
	 */
	public CRLFLineWriter getOutput() throws IOException {
		if( output == null ) {
			synchronized (outputLock) {
				if( output == null ) {
					output = new CRLFLineWriter(getSocket().getOutputStream());
				}
			}
		}
		return output;
	}


	/**
	 * @return The port currenty defined for this connection.
	 */
	public int getPort() {
		return port;
	}

	/**
	 * @param port to connect to
	 */
	public void setPort(int port) {
		this.port = port;
	}

	/**
	 * @return A Socket connected to the current host and port.
	 * 
	 * @throws IOException
	 */
	private Socket getSocket() throws IOException {
		if( socket == null ) {
			synchronized (socketLock) {
				if( socket == null ) {
					sslSocket = null;// just in case :-)
					String host = getHost();
					int port = getPort();
					logDebug("Attempt connect to "+host+":"+port);
					int timeout = getCmdTimeout();
					int linger = getCmdLinger();
					// Unconnected first so we can use a connect timeout
					Socket tmp = getSocketFactory().createSocket();
					try {
						tmp.setKeepAlive(true);
						tmp.setTcpNoDelay(true);
						tmp.connect(new java.net.InetSocketAddress(host, port), timeout);
						tmp.setSoTimeout(timeout);
						if( linger > 0 ) {
							tmp.setSoLinger(true, linger);
						}
					} catch (IOException e) {
						try {
							tmp.close();
						} catch (IOException e1) {
						}
						throw e;
					}
					logDebug("Connected to "+host+":"+port+" timeout="+timeout+" linger = "+linger);
					socket = tmp;
				}				
			}
		}

		if( sslSocket == null ) {
			return socket;
		} else {
			return sslSocket;
		}
	}



	/**
	 * Write one line of text to the command channel.
	 *  
	 * @param line 
	 * @throws IOException
	 */
	public void writeLine(String line) throws IOException {
		String safe = maskCredentials(line);
		if( isDebugEnabled() ) {
			logDebug(""+Thread.currentThread().hashCode()+" Write:"+safe);
		}
		CRLFLineWriter out = getOutput();
		out.writeLine(line);
		out.flush();
		appendDialog(" Write:"+safe);
	}

	/** Maximum number of characters kept in {@link #dialog}. */
	public static final int MAX_DIALOG_SIZE = 64 * 1024;

	/**
	 * Replace the argument of a PASS command so passwords never reach logs or the dialog.
	 */
	static String maskCredentials(String line) {
		if( line != null && line.length() >= 4 && line.regionMatches(true, 0, PASS, 0, 4)
				&& (line.length() == 4 || line.charAt(4) == ' ') ) {
			return PASS+" ****";
		}
		return line;
	}

	private void appendDialog(String text) {
		synchronized (dialog) {
			dialog.append(Thread.currentThread().hashCode()).append(text).append('\n');
			if( dialog.length() > MAX_DIALOG_SIZE ) {
				// keep the most recent half
				dialog.delete(0, dialog.length() - MAX_DIALOG_SIZE/2);
			}
		}
	}

	/**
	 * @return one line of text read from the command channel.
	 * 
	 * @throws IOException
	 */
	protected String readLine() throws IOException {
		String ret = getInput().readLine();
		if( ret == null ) {
			peerClosed = true;
		}
		if( isDebugEnabled() ) {
			logDebug(""+Thread.currentThread().hashCode()+" Read: "+ret);
		}
		appendDialog(" Read: "+ret);
		return ret;
	}

	/**
	 * Drop the control connection without sending QUIT. Used when the conversation with
	 * the server is out of step (e.g. a reply timed out); the next command reconnects.
	 */
	synchronized void abandonConnection() {
		Socket s = socket;
		socket = null;
		sslSocket = null;
		input = null;
		output = null;
		connected = false;
		mlstTested = false;
		featResponse = null;
		if( !isSecure() && channelSecure ) {
			setSocketFactory(SocketFactory.getDefault());
			setServerSocketFactory(ServerSocketFactory.getDefault());
		}
		channelSecure = false;
		dataChannelSecure = false;
		if( s != null ) {
			try {
				s.close();
			} catch (IOException e) {
			}
		}
	}

	/**
	 * Close the connect.  If connected a Quit command is send to the server.
	 */
	public synchronized  void close() {
		if(connected ) {
			try {
				ClientFtpResponse res = sendCommand(QUIT);
				if( !res.isPositiveComplet()) {
					logDebug("Invalid resp from quit ="+res);
				}
			} catch(Exception ex) {}
		}

		if( socket != null ) {
			int linger = getCmdLinger();
			if( linger > 0 ) {
				try {
					socket.setSoLinger(true, linger);
					/*
					 *  The timeout overrides the linger, 
					 *  Set the timeout to 1sec longer than linger
					 */
					socket.setSoTimeout((linger*1000)+1000);
				} catch (SocketException e) {
				}

			}
			try {
				socket.close();
			} catch(Exception ex) {}
		}
		socket = null;
		input = null;
		output = null;
		mlstTested = false;
		connected = false;
		if( !isSecure() && channelSecure) {
			//  reset these to defaults.
			setSocketFactory(SocketFactory.getDefault());
			setServerSocketFactory(ServerSocketFactory.getDefault());
		}
		// A new connection must negotiate AUTH again. Previously channelSecure stayed true,
		// so a reconnect skipped AUTH and sent USER/PASS in clear text.
		channelSecure = false;
		dataChannelSecure = false;
		sslSocket = null;
		// a new session starts in the server's default directory
		currentDir = null;
		epsvRejected = false;
	}

	/**
	 * Send a command to the server and read the response.
	 * 
	 * @param command
	 * @return The ClientFtpResponse from the server
	 * @throws UnknownHostException
	 * @throws IOException
	 * 
	 */
	public synchronized ClientFtpResponse executeCommand(String command) throws  IOException {
		String name = firstToken(command);
		if( !streamsInProcess.isEmpty() ) {
			// One control connection can only run one transfer; interleaving commands with
			// an open stream reads the wrong replies.
			throw new IOException("A transfer is in progress ("+streamsInProcess.keySet()+"); close its stream before sending "
					+name+", or use a separate FtpClient for concurrent transfers.");
		}
		boolean wasConnected = connected;
		if( !connected ) {
			reconnect();
		}

		peerClosed = false;
		IOException failure = null;
		ClientFtpResponse res = null;
		try {
			res = sendCommand(command);
		} catch (java.net.SocketTimeoutException e) {
			throw e;
		} catch (IOException e) {
			failure = e;
		}
		if( failure == null && !peerClosed ) {
			return res;
		}

		// The server closed the connection (e.g. idle timeout) or the network failed.
		abandonConnection();
		if( autoReconnect && wasConnected && RETRY_SAFE.contains(name) ) {
			logInfo("Control connection to "+getHost()+" was closed, reconnecting to retry "+name);
			reconnect();
			return sendCommand(command);
		}
		if( failure != null ) {
			throw new IOException("Connection to "+getHost()+" lost during "+name+"; it will be reopened on the next command", failure);
		}
		return res; // the 421 left by a closed connection
	}

	/**
	 * Connect again with the saved credentials and restore the current directory.
	 */
	private void reconnect() throws IOException {
		String dir = currentDir;
		if( !connect(userId,password,account) ) {
			throw new IOException("Can't reconnect to "+getHost()+":"+getPort()+" as "+userId);
		}
		if( dir != null ) {
			ClientFtpResponse res = sendCommand(CWD+" "+dir);
			if( res.isPositiveComplet() ) {
				currentDir = dir;
			} else {
				logError("Can't restore directory "+dir+" after reconnect: "+res);
				currentDir = null;
			}
		}
	}

	private static String firstToken(String command) {
		String c = command.trim();
		int idx = c.indexOf(' ');
		return (idx < 0 ? c : c.substring(0, idx)).toUpperCase(java.util.Locale.ROOT);
	}

	/**
	 * @return true (default) to reconnect and retry safe commands once when the server
	 * has closed the control connection.
	 */
	public boolean isAutoReconnect() {
		return autoReconnect;
	}

	public void setAutoReconnect(boolean autoReconnect) {
		this.autoReconnect = autoReconnect;
	}

	/**
	 * @return true (default) to try EPSV before PASV.
	 */
	public boolean isUseEpsv() {
		return useEpsv;
	}

	/**
	 * @param useEpsv true (default) to use EPSV (RFC 2428, needed for IPv6) and fall back
	 * to PASV when the server doesn't support it; false to always use PASV.
	 */
	public void setUseEpsv(boolean useEpsv) {
		this.useEpsv = useEpsv;
	}

	boolean isEpsvRejected() {
		return epsvRejected;
	}

	void setEpsvRejected(boolean rejected) {
		this.epsvRejected = rejected;
	}

	/**
	 * @return the local address of the control connection (used for PORT/EPRT).
	 */
	java.net.InetAddress getControlLocalAddress() throws IOException {
		return getSocket().getLocalAddress();
	}

	/**
	 * This is used by all commands and it does not requires we're currently logged in 
	 * @param command
	 * @return ClientFtpResponse 
	 * @throws IOException
	 */
	private ClientFtpResponse sendCommand(String command) throws  IOException {
		writeLine(command);        
		lastResponse = readResponse(); 
		return lastResponse;
	}
	/**
	 * Send a command with one argument to the server
	 * 
	 * @param command 
	 * @param arg
	 * @return The ClientFtpResponse from the server
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public ClientFtpResponse executeCommand(String ... args) throws IOException {
		StringBuilder buf = new StringBuilder();
		for (int idx = 0; idx < args.length; idx++) {
			if(idx > 0 ) {
				buf.append(' ');
			}
			buf.append(args[idx]);
		}
		return executeCommand(buf.toString());
	}

	/**
	 * Connect using the specified userId and password.
	 * 
	 * @param userId
	 * @param passwd
	 * @return true if the client is able to connect to the server.
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	public synchronized boolean connect(String userId, String passwd, String account) throws IOException {
		if( !connected ) {
			mlstTested = false;
			this.userId = userId;
			this.password = passwd;
			this.account = account;
			logDebug("userid="+userId+" account="+account);

			// connecting a socket will trigger the server to send us a greeting line
			ClientFtpResponse res = readResponse();
			if( res.isPositiveComplet()) {
				if( !isSecure() ) {
					boolean ok = executeAuth();
					if( isRequireSecure() && !ok) {
						return false;
					}
				}

				res = sendCommand(USER+" "+userId);

				if( res.isPositiveIntermediate()) {
					res = sendCommand(PASS+" "+passwd);
					// 332 asks for an account (RFC 959). A server may also answer 530 and
					// accept the account in a following ACCT, as the BJL server does
					// (user@account logins), so a configured account is tried once either way.
					int code = res._getResponseCode();
					if( account != null && !account.isEmpty()
							&& (code == REPLY_332_NEED_ACCOUNT || code == REPLY_530_USER_NOT_LOGGED_IN)) {
						res = sendCommand(ACCT+" "+account);
					}
				}

				if( res.isPositiveComplet()) {
					connected = true;
					if( isSecure() || isChannelSecure() ) {
						negotiateDataProtection();
					}
				}

			}

			//  All done, if we're not connected we need to close socket
			if( !connected) {
				try {
					close();	
				} catch (Exception e) {
				}
			}
		}    


		return connected;
	}

	protected FtpClient getNewConnection() throws  IOException {
		FtpClient ret = new FtpClient(getHost(),getPort());
		ret.useSsl = useSsl;
		ret.connect(userId,password,account);

		//  The response should always be the same so we only need to do this once.
		ret.featResponse = getFeatResponse();
		return ret;

	}

	/**
	 * Local helper to parse a directory name from the 
	 * response text of an FTP command (PWD)
	 * 
	 * @param dirName the text from a FTP response
	 * @return a directory name parsed from the text
	 */
	private String parserDirectoryName(String dirName) {
		/*
		 * The next line is a typical response.  We need to pull the data out. 
		 * "/path/path" is the current directory
		 */
		String ret = dirName;
		int idx1 = ret.indexOf('"');
		if( idx1 > 0 ) {
			int idx2 = ret.indexOf('"',++idx1);
			if( idx2 > 0 ) {
				ret = ret.substring(idx1,idx2);
			}
		}
		return ret;

	}

	/**
	 * 
	 * Execute the FTP CWD Command
	 * 
	 * @param dirName
	 * @return true if the command succeed
	 * @throws IOException
	 * @throws UnknownHostException
	 */
	public  synchronized boolean executeCwd(String dirName) throws IOException {

		ClientFtpResponse res = executeCommand(CWD,dirName); 
		boolean ret = res.isPositiveComplet();
		// Force a PWD to get the correct value (also needed to restore it after a reconnect)
		currentDir = null;
		if( ret ) {
			executePwd();
		}
		return ret;
	}


	public boolean setImageType() throws IOException {
		return executeType(TYPE_IMAGE);
	}

	public boolean setAsciiType() throws IOException {
		return executeType(TYPE_ASCII);
	}

	private boolean executeType(String type) throws IOException {

		ClientFtpResponse res = executeCommand(TYPE,type);
		boolean ret = res.isPositiveComplet();

		return ret;
	}

	/**
	 * Execute the CDUP Command 
	 * @return true is successful
	 * @throws IOException
	 */
	public boolean executeCdup() throws IOException {
		ClientFtpResponse res = executeCommand(CDUP);
		currentDir=null;
		boolean ret = res.isPositiveComplet();
		if( ret ) {
			executePwd();
		}

		return ret;
	}


	/**
	 * @return The current working directory of the FTP session 
	 * @throws IOException
	 */
	public String executePwd() throws IOException {

		if( currentDir == null ) {

			ClientFtpResponse res = executeCommand(PWD);
			if(res.isPositiveComplet()) {
				currentDir = parserDirectoryName(res.getResponseText());
			} else {
				logError("Can't change directory response = "+res);
			}
		}

		return currentDir;
	}

	/*
	 * 250 CWD command successful.
/hold/TDM/config/Tdm-14.0.11  loaded from [Directory Listing Cache]DIR3D.tmp
PWD
257 "/hold/TDM/config/Tdm-14.0.11" is current directory.
TYPE A
200 Type set to A; form set to N.
PASV
227 Entering Passive Mode (10,129,15,18,200,135)
connecting data channel to 10.129.15.18:51335
data channel connected to 10.129.15.18:51335
LIST
150 Opening data connection for /bin/ls.
transferred 3358 bytes in 0.016 seconds, 1679.000 Kbps ( 209.875 KBps), transfer succeeded.

	 */


	/**
	 * @return timewout value used for the command socket. 
	 */
	public int getCmdTimeout() {
		return cmdTimeout;
	}


	/**
	 * Set the timeout values used for the command socket.
	 * @param cmdTimeout in milliseconds.
	 */
	public void setCmdTimeout(int cmdTimeout) {
		this.cmdTimeout = cmdTimeout;
	}


	/**
	 * @return true is currently connected to a server.
	 */
	public boolean isConnected() {
		return connected;
	}


	/**
	 * Execute the SIZE command
	 * @param path file name
	 * @return size of the file or -1 if size is not available (maybe file does not exists).
	 * @throws IOException
	 */
	public long executeSize(String path) throws IOException {
		long ret = -1;
		ClientFtpResponse resp = executeCommand(SIZE+" "+path);
		if( resp.isPositiveComplet()) {
			String tmp = resp.getResponseText().trim();
			try {
				ret = Long.parseLong(tmp);	
			} catch (Exception e) {
			}
		}

		return ret;
	}

	/**
	 * RFC 4217: after logging in on a secure control connection, ask for protected data
	 * connections (PBSZ 0, PROT P). If the server refuses PROT P, data connections are clear.
	 * A server that doesn't know PBSZ at all is treated as a legacy server that always uses
	 * TLS for data after AUTH (older versions of this project's server).
	 */
	private void negotiateDataProtection() throws IOException {
		ClientFtpResponse res = sendCommand(PBSZ+" 0");
		if( !res.isPositiveComplet() ) {
			logDebug("Server does not support PBSZ ("+res+"), assuming protected data connections");
			dataChannelSecure = true;
			return;
		}
		res = sendCommand(PROT+" P");
		dataChannelSecure = res.isPositiveComplet();
		if( !dataChannelSecure ) {
			logInfo("Server refused PROT P ("+res+"), data connections will not be encrypted");
		}
	}

	/**
	 * @return true if data connections are encrypted.
	 */
	public boolean isDataChannelSecure() {
		return dataChannelSecure;
	}

	/**
	 * @return the SocketFactory for passive data connections: TLS when the data channel is
	 * protected, plain otherwise.
	 */
	public SocketFactory getDataSocketFactory() throws IOException {
		return dataChannelSecure ? getSocketFactory() : SocketFactory.getDefault();
	}

	/**
	 * Puts TLS on a connected data socket, as the TLS client (RFC 4217), so that it resumes
	 * the control connection's TLS session, which RFC 4217 recommends and servers such as
	 * vsftpd (require_ssl_reuse) and FileZilla Server require (BJL-18).
	 * <p>
	 * Java looks a client session up by host and port. An SSLSocket always uses the port it
	 * is connected to (the data port), so it never found the control session; an SSLEngine
	 * uses the host and port it is created with, so the data connection runs through one
	 * created with the control connection's. It must come from the control connection's
	 * SSLContext, which holds the session.
	 *
	 * @param plain a connected, plain data socket; closed with the returned socket
	 * @return the TLS socket; the handshake happens on first use
	 */
	public Socket secureDataSocket(Socket plain) throws IOException {
		SSLEngine engine = getSSLContext().createSSLEngine(getHost(), getPort());
		SslEngineSocket ssl = new SslEngineSocket(plain, engine);
		ssl.setSoTimeout(plain.getSoTimeout());
		return ssl;
	}

	/**
	 * @return the ServerSocketFactory for active data connections.
	 */
	public ServerSocketFactory getDataServerSocketFactory() throws IOException {
		return dataChannelSecure ? getServerSocketFactory() : ServerSocketFactory.getDefault();
	}

	/**
	 * @return ServerSocketFactory used to create ServerSockets 
	 * @throws IOException 
	 */
	public ServerSocketFactory getServerSocketFactory() throws IOException {
		if( serverSocketFactory == null ) {
			if( isSecure()) {
				serverSocketFactory = getSSLContext().getServerSocketFactory();
			} else {
				serverSocketFactory = ServerSocketFactory.getDefault();
			}
		}
		return serverSocketFactory;
	}

	/**
	 * @param serverSocketFactory Factory to use when creating ServerSockets
	 */
	public void setServerSocketFactory(ServerSocketFactory serverSocketFactory) {
		this.serverSocketFactory = serverSocketFactory;
	}



	/**
	 * @return timeout used when transferring data (in milliseconds)
	 */
	public int getTransferTimeout() {
		return txferTimeout;
	}

	/**
	 * @param timeout value used when transferring data (in milliseconds)
	 */
	public void setTransferTimeout(int timeout) {
		txferTimeout = timeout;
	}


	/**
	 * @return The list of file entries for the current directory.
	 * @throws IOException
	 */
	public String[] executeList() throws IOException {
		return executeList(
				executePwd()
				);    
	}

	public boolean isMlstSupported() throws IOException {

		if( !mlstTested ) {
			synchronized (this) {
				if( !mlstTested ) {

					String tmp = (String) getFeatResponse().get(MLST);
					mlstTested = true;

					if( tmp != null ) {
						/*
						 * Tell the server witch facts we want.  If any are
						 * not supported then we need to use LIST instead.
						 */
						//  must support at least these permissions

						ClientFtpResponse res = executeCommand(OPTS+" "+MLST
								+" "
								// use permissions from list +PERM+";"
								+TYPE+";"
								+MODIFY+";"
								+SIZE+";"
								);
						
						mlstSupported = res.isPositiveComplet();						
					}
				}
			}
		}

		return mlstSupported;
	}

	private ClientFtpResponse sendMlsdOrList(String dirPath, boolean useList) throws IOException {
		ClientFtpResponse ret = null;

		if( !useList && isMlstSupported() ) {
			/*
			 * MLST is the preferred method.  It is clear and platform independent.
			 * If the server does not support MLST this will only exec once.
			 */
			ret = executeCommand(MLSD,dirPath);
		} else { 
			/*
			 *Create the list from server that doesn't not support MLST.
			 * 
			 */
			ret = executeCommand(LIST,dirPath);
		} 

		return ret;
	}


	public synchronized String[] executeList(boolean dontUseMlst, String dirPath) throws IOException {
		// Pass the choice down instead of temporarily changing the shared forceList field
		return list(dirPath, dontUseMlst);
	}

	/**
	 * Execute the LIST command on the specified directory and
	 * 
	 * @param dirPath
	 * @return The list of file entries for the specified directory.
	 * @throws IOException
	 */
	public synchronized String[] executeList(String dirPath) throws IOException {
		return list(dirPath, forceList);
	}

	private String[] list(String dirPath, boolean useList) throws IOException {

		/*
		if(!setAsciiType()) {
			throw new IllegalStateException("Can't set type to ascii."); 
		}
		*/
		String [] ret = null;

		ClientDataTransferProcess dtp = getDataTransferProcess();
		CRLFLineReader in = null;
		try {
			dtp.connectBeforeCommand();
			ClientFtpResponse res = sendMlsdOrList(dirPath, useList);


			if( res.isPositivePreliminay()) {
				// Active mode accepts the server's connection here, after the 1xx reply
				in = new CRLFLineReader(dtp.getInput());
				String line = null;
				List<String> list = new ArrayList<String>();
				while((line=in.readLine()) != null) {
					line = line.trim();
					if( isDebugEnabled()) {
						logDebug("Line="+line);
					}

					if( line.length() > 20) {
						// 20 is a min len for a file entry.  If it's less than that, it probably server dialog.
						list.add(line);
					}
				}
				dtp.close();
				ret = (String [])list.toArray(new String[list.size()]);
				res = readResponse();
				if( !res.isPositiveComplet()) {
					logError("Invalid response after list ="+res);
				}
			}
		} finally {
			if( in != null ) {
				try {
					in.close();
				} catch (Exception e) {
				}
			}
			// releases the socket or active listener if the command was refused
			dtp.close();
		}
		return ret;
	}





	protected ClientDataTransferProcess getDataTransferProcess() throws IOException {
		//  To support active we would need a psv/actv flag
		if( active ) {
			return  new ClientActiveDataConnection(this);
		} else {
			return  new ClientPassiveDataConnection(this);
		}

	}


	protected ClientFtpResponse readResponse() throws IOException {
		ClientFtpResponse ret = new ClientFtpResponse();
		ret.readResponse(this);

		return ret;
	}

	/**
	 * Open an input stream to a remote file
	 * @param path to remote file
	 * @param ascii type of transfer
	 * @param startingPos
	 * @return
	 * @throws IOException
	 */
	public synchronized InputStream getInputStream(String path, boolean ascii, long startingPos) throws IOException {
		checkStreamInProcess(path);
		ClientFtpInputStream ret = new ClientFtpInputStream(path,this,ascii, startingPos);
		streamsInProcess.put(path, ret);

		return ret;
	}

	/**
	 * Open an input stream to a remote file
	 * @param path
	 * @param ascii
	 * @return
	 * @throws IOException
	 */
	public InputStream getInputStream(String path, boolean ascii) throws IOException {
		return getInputStream(path,ascii,0);
	}

	/**
	 * OPen an oputput stream to a remote file
	 * @param path
	 * @return	the OutputStream
	 * @throws IOException
	 */
	public OutputStream getOutputStream(String path) throws IOException {
		return getOutputStream(path,false);
	}

	public OutputStream getAppendOutputStream(String path) throws IOException {
		return getOutputStream(path,false,true);
	}

	public synchronized OutputStream getOutputStream(String path, boolean ascii, boolean append) throws IOException {
		checkStreamInProcess(path);

		ClientFtpOutputStream ret = new ClientFtpOutputStream(path,this,ascii, append);
		streamsInProcess.put(path, ret);

		return ret;
	}



	public OutputStream getOutputStream(String path, boolean ascii) throws IOException {
		return getOutputStream(path,ascii, false);
	}


	public boolean rename(String from, String to ) throws IOException {
		checkStreamInProcess(from);
		checkStreamInProcess(to);
		boolean ret = false;
		ClientFtpResponse res = executeCommand(RNFR,from);
		if( res.isPositiveIntermediate()) {
			res = executeCommand(RNTO,to);
			ret = res.isPositiveComplet();
		}

		return ret;
	}

	public boolean delete(String path) throws IOException {
		boolean ret = false;
		checkStreamInProcess(path);
		ClientFtpResponse res = executeCommand(DELE,path) ;
		ret = res.isPositiveComplet();
		return ret;
	}

	private synchronized void checkStreamInProcess(String path) throws IOException {
		// Any open stream blocks the control connection, not just one for the same path
		if( !streamsInProcess.isEmpty()) {
			throw new IOException("A transfer is in progress ("+streamsInProcess.keySet()+"). Close its stream before starting "
					+path+", or use a separate FtpClient for concurrent transfers.");
		}	
	}

	public boolean mkDir(String path) throws IOException {
		checkStreamInProcess(path);
		boolean ret = false;
		ClientFtpResponse res = executeCommand(MKD,path);
		ret = res.isPositiveComplet();

		return ret;
	}

	public boolean rmDir(String path) throws IOException {
		checkStreamInProcess(path);
		boolean ret = false;
		ClientFtpResponse res = executeCommand(RMD,path);
		ret = res.isPositiveComplet();

		return ret;
	}

	public ClientFtpResponse getLastResponse() {
		return lastResponse;
	}

	public boolean mkDirs(String absolutePath) throws IOException {
		checkStreamInProcess(absolutePath);
		boolean ret = mkDir(absolutePath);
		if( !ret ) {
			int idx = absolutePath.lastIndexOf(SEPERATOR_CHAR);
			if( idx > 0 ) {
				String path = absolutePath.substring(0,idx);
				if(mkDirs(path)) {
					ret = mkDir(absolutePath);
				}
			}
		}
		return ret;
	}

	public InputStream getInputStream(String remote) throws IOException {

		return getInputStream(remote,false);
	}

	/**
	 * @return A Map containing the features supported by the server (response to a FEAT command)
	 * @throws IOException
	 */
	public Map<String, String> getFeatResponse() throws IOException {
		if( featResponse == null ) {
			ClientFtpResponse res = executeCommand(FEAT);
			featResponse = new HashMap<String, String>();
			// If FEAT is not supported, this will be empty.
			if( res.isPositiveComplet()) {

				String[] lines = res.getResponseText().split("\n");
				if( lines != null && lines.length>2) {
					/**

                    Replies to the FEAT command MUST comply with the following syntax.
                    Text on the first line of the reply is free form, and not
                    interpreted, and has no practical use, as this text is not expected
                    to be revealed to end users.  The syntax of other reply lines is
                    precisely defined, and if present, MUST be exactly as specified.

                         feat-response   = error-response / no-features / feature-listing
                         no-features     = "211" SP *TCHAR CRLF
                         feature-listing = "211-" *TCHAR CRLF
                                           1*( SP feature CRLF )
                                           "211 End" CRLF
                         feature         = feature-label [ SP feature-parms ]
                         feature-label   = 1*VCHAR
                         feature-parms   = 1*TCHAR

					 */

					for (int idx = 1,sz=lines.length-1; idx < sz; idx++) {
						lines[idx]=lines[idx].trim();
						String tmp = lines[idx];
						int pos = tmp.indexOf(' ');
						if( pos > 0 ) {
							tmp = tmp.substring(0,pos);
						}
						featResponse.put(tmp, lines[idx]);
					}
				}
			}
		}

		return featResponse;
	}

	/**
	 * @return true if the client connects to the address in the server's PASV reply,
	 * false (default) to use the control connection's host.
	 */
	public boolean isUsePasvAddress() {
		return usePasvAddress;
	}

	/**
	 * @param usePasvAddress true to connect to the address in the PASV reply (only needed
	 * when the server deliberately sends a different host, e.g. FXP). Default false.
	 */
	public void setUsePasvAddress(boolean usePasvAddress) {
		this.usePasvAddress = usePasvAddress;
	}

	public int getTransferBufferSize() {		
		return this.transferBufferSize ;
	}



	/**
	 * 
	 * @return true if the client should request a secure channel user AUTH command (RFC 2228)
	 */
	public boolean isRequestSecure() {
		return requestSecure;
	}

	/**
	 * if true the client will request a secure channel user AUTH command (RFC 2228)
	 * @param requestSecure
	 */
	public void setRequestSecure(boolean requestSecure) {
		this.requestSecure = requestSecure;
	}

	/**
	 * If requireSecure is true the client will refuse to make an insecure connection.
	 *  
	 * @return true if the client should require a secure channel
	 */
	public boolean isRequireSecure() {
		return requireSecure;
	}

	/**
	 * If requireSecure is true the client will refuse to make an insecure connection.
	 * 
	 * @param requireSecure
	 */
	public void setRequireSecure(boolean requireSecure) {
		this.requireSecure = requireSecure;
	}

	/**
	 * This is called before signing in when the connection is not secure.
	 * If the server does not accept the AUTH then the channel will stay un-secured. 
	 * @return true if a secure channel was negotiated.
	 * 
	 * @throws IOException only in case a communication problem.
	 */
	private boolean executeAuth() throws IOException {
		logDebug("Enter executeAuth");
		if( isChannelSecure() || isSecure()) {
			return true;
		}
		boolean ret = false;

		if( isRequestSecure()) {
			for(String type : SECURE_TYPES) {
				ClientFtpResponse res = sendCommand(AUTH+" "+type);
				if( res.isPositiveComplet()) {
					try {
						negotiateSecureSocket(type);
						ret = true;
						break;
					} catch (Throwable e) {
						logError("Server accepted the AUTH command but failed to establish a secure connection", e);						
					}
				}
			}
		}
		logDebug("Exit executeAuth now secure="+ret);
		return ret;
	}

	/**
	 * In many cases, if a secure socket is required, the
	 * SocketFactory takes care of the details when a connection is established.
	 * 
	 * In some cases you need to change an existing socket 
	 * to a secure socket.
	 * 
	 * @param sslOrTsl
	 * @throws KeyManagementException
	 * @throws CertificateException
	 * @throws FileNotFoundException
	 * @throws KeyStoreException
	 * @throws NoSuchAlgorithmException
	 * @throws UnrecoverableKeyException
	 * @throws IOException
	 */
	private void negotiateSecureSocket(String sslOrTsl) throws  IOException {
		if( sslSocket != null ) {
			sslSocket.close();
			sslSocket = null;
		}

		if( sslOrTsl == null ) {
			return;
		}


		if( isSecure()) {
			throw new IllegalStateException("Can not negotiate a secure channel from a secure channel.");
		}
		setProtocol(sslOrTsl);
		SSLContext ctx=getSSLContext();
		SSLSocketFactory factory = ctx.getSocketFactory();

		// With the host (it was null), Java caches the session under this host and port,
		// which is what lets data connections resume it (see secureDataSocket, BJL-18)
		sslSocket = (SSLSocket)factory.createSocket(socket,getHost(), socket.getPort(), false);
		sslSocket.setWantClientAuth(false);
		sslSocket.startHandshake();
		channelSecure = true;
		input = null;
		output = null;

		//  future sockets will be secure using this context
		setSocketFactory(factory);
		setServerSocketFactory(ctx.getServerSocketFactory());


	}

	protected synchronized void streamHasClosed(String path, AutoCloseable stream) {
		AutoCloseable obj = streamsInProcess.remove(path);
		if( obj == null ) {
			logInfo(path+" did not have a stream in process.");
		}

	}

	@Override
	public void logDebug(String msg) {
		if( isSecure()) {
			msg = "(S) "+msg;
		}
		if( isChannelSecure()) {
			msg = "(CS) "+msg;
		}
		super.logDebug(msg);
	}

	@Override
	public void logDebug(String msg, Throwable error) {
		if( isSecure()) {
			msg = "(S) "+msg;
		}
		if( isChannelSecure()) {
			msg = "(CS) "+msg;
		}

		super.logDebug(msg, error);
	}
}
