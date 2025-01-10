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
	private SSLSocket sslSocket;
	public StringBuilder dialog = new StringBuilder();
	private volatile CRLFLineReader input;
	private volatile CRLFLineWriter output;
	private boolean connected = false;
	//  Socket timeout in milliseconds
	private int cmdTimeout = 60000;
	//  Socket linger in seconds
	private int cmdLinger = 120;
	private int txferTimeout = 60000;
	private int transferLinger = 600;    
	private String currentDir;
	private ClientFtpResponse lastResponse;

	private String userId;
	private String password;
	private String account;
	private Map<String, String> featResponse;
	private Map<String,AutoCloseable> streamsInProcess = new HashMap<>();
	




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
	private boolean active = false;
	private boolean channelSecure;
	public boolean forceList;


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
					Socket tmp = getSocketFactory().createSocket(host,port);
					int timeout = getCmdTimeout();
					int linger = getCmdLinger();
					logDebug("Connected to "+host+":"+port+" setting timeout="+timeout+" linger = "+linger);
					tmp.setSoTimeout(timeout);
					if( linger > 0 ) {
						tmp.setSoLinger(true, linger);
					}

					tmp.setKeepAlive(true);
					tmp.setTcpNoDelay(true);
					tmp.setReceiveBufferSize(64*1024);
					tmp.setSendBufferSize(64*1024);
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
		logDebug(
				""+Thread.currentThread().hashCode()+" Write:"+line
				);
		CRLFLineWriter out = getOutput();
		out.writeLine(line);
		out.flush();
		dialog.append(""+Thread.currentThread().hashCode()+" Write:"+line+"\n");
	}

	/**
	 * @return one line of text read from the command channel.
	 * 
	 * @throws IOException
	 */
	protected String readLine() throws IOException {
		String ret = getInput().readLine();
		logDebug(""+Thread.currentThread().hashCode()+" Read: "+ret);
		dialog.append(""+Thread.currentThread().hashCode()+" Read: "+ret+"\n");
		return ret;
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
		if( !connected ) {
			connect(userId,password,account);
		}
		return sendCommand(command);
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
			logDebug("userid="+userId+" password = "+passwd+" account="+account);

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
					if(res._getResponseCode() == REPLY_332_NEED_ACCOUNT) {
						res = sendCommand(ACCT+" "+account);
					}
				}

				if( res.isPositiveComplet()) {
					connected = true;
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
		// Force a PWD to get the correct value
		currentDir = null;
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

	private ClientFtpResponse sendMlsdOrList(String dirPath) throws IOException {
		ClientFtpResponse ret = null;

		if( !forceList && isMlstSupported() ) {
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
		boolean tmp= forceList;
		forceList = dontUseMlst;
		String ret [] = executeList(dirPath);
		forceList = tmp;
		return ret;
	}

	/**
	 * Execute the LIST command on the specified directory and
	 * 
	 * @param dirPath
	 * @return The list of file entries for the specified directory.
	 * @throws IOException
	 */
	public synchronized String[] executeList(String dirPath) throws IOException {

		/*
		if(!setAsciiType()) {
			throw new IllegalStateException("Can't set type to ascii."); 
		}
		*/
		String [] ret = null;

		ClientDataTransferProcess dtp = getDataTransferProcess();
		CRLFLineReader in = new CRLFLineReader(dtp.getInput());
		try {
			ClientFtpResponse res = sendMlsdOrList(dirPath);


			if( res.isPositivePreliminay()) {
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
			try {
				in.close();
			} catch (Exception e) {
			}
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
		if( streamsInProcess.containsKey(path)) {
			throw new IOException(path+" already has a transfer in progress.  Make sure to close any stream before attempting another operation. ");
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

		sslSocket = (SSLSocket)factory.createSocket(socket,null, socket.getPort(), false);
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
