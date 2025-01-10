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
 * ~version~V000.01.55-V000.01.53-V000.01.48-V000.01.47-V000.01.46-V000.01.42-V000.01.41-V000.01.37-V000.01.35-V000.01.33-V000.01.30-V000.01.25-V000.01.18-V000.01.12-V000.01.09-V000.01.07-V000.01.05-V000.00.02-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.net.ftp.server;


import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import us.bringardner.core.ILogger;
import us.bringardner.core.util.ThreadSafeDateFormat;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.net.framework.server.AbstractCommandProcessor;
import us.bringardner.net.framework.server.IPrincipal;
import us.bringardner.net.ftp.FTP;

/**
 * @author Tony Bringardner
 *
 */
public class FtpRequestProcessor extends AbstractCommandProcessor implements FTP {

	private static final long serialVersionUID = 1L;

	public static final int TYPE_ASCII	 = 0;
	public static final int TYPE_IMAGE	 = 1;
	public static final String UNRECOGNIZED_COMMAND="UNRECOGNIZED_COMMAND";
	public static final String NOT_AUTHORIZED_COMMAND="NOT_AUTH_COMMAND";

	public static final ThreadSafeDateFormat formatter = new ThreadSafeDateFormat ("yyyy-MM-dd hh:mm:ss ");

	public static final String PARAMETER_ROOT = "userRoot";
	public static final String PARAMETER_DEFAULT_DIRECTORY = "defaultDir";

	public class StreamController {
		private FtpServerStream stream = new FtpServerStream();

		// Abort the transfer (ABOR)
		public synchronized void abort() throws IOException {
			synchronized (stream.lock) {
				if( !stream.isRunning()) {
					reply(us.bringardner.net.ftp.FTP.REPLY_226_CLOSING_DATA_CON,"01 abort ok");
				} else {
					stream.abort();
				}
			}

		}


		public synchronized void start(FtpServerStream stream2) throws IOException {
			synchronized (stream.lock) {
				if(stream.isRunning()) {
					stream.processor.reply(REPLY_425_CANT_OPEN_DATA_CON, "Data transfer already in process");
				} else {
					stream = stream2;
					stream.start();
					reply(REPLY_125_DATA_CON_ALREADY_OPEN,"Starting "+(stream.processor.isAsciiMode() ? "Ascii":"Binary")+" transfer");
				}	
			}

		}


	}
	/* 
	 * This is used to temporarily store vales that are provided in
	 * one command and the needed by another commnad (USER & PASS for instance).
	 */ 
	private Map<String, Object> tempStorage = new HashMap<String, Object>();  

	private String rootName ;
	private int rootNameLen = 0;
	private FileSource ftpRoot ;
	private FileSource currentDir ;
	private FileSourceFactory factory;
	private boolean allowAnonymous = true;
	private int representationType = TYPE_ASCII;


	private FileSource tmp;
	private PassiveSocket pasvSocket; 	
	private boolean passive = false;
	private Socket dataSocket;
	private long lastActivity = 0;

	//  Time out if inactive
	private int activityTimeOut = 10 * (60*1000);

	private boolean channelSecure = false;
	private int pbsz=-1;
	private String protLevel = DATA_CHANNEL_PROTECTION_LEVEL_CLEAR;
	private int loginAttempts=0;
	private int linger = -2;
	public StreamController transferInProcess = new StreamController();



	public FileSource getFtpRoot() {
		return ftpRoot;
	}

	public void setFtpRoot(FileSource ftpRoot) throws IOException {

		logDebug("Set ftpRoot = "+ftpRoot.getAbsolutePath());
		if( !ftpRoot.exists() ){
			if(!ftpRoot.mkdirs()) {
				throw new IOException("Cannot create ftp root at "+ftpRoot);
			}
		} else if( !ftpRoot.isDirectory() ) {
			throw new RuntimeException("Invalide root dir = "+ftpRoot);
		}
		this.ftpRoot = ftpRoot;

		//  This is just to improve performance determining the display file name.
		try {
			rootName = ftpRoot.getCanonicalPath();
			logDebug("rootName="+rootName);
		} catch (IOException e) {
			e.printStackTrace();
			rootName = "Undefined";
		}
		rootNameLen = rootName.length();

		setCurrentDir(ftpRoot);
	}


	/*
	 * Authenticate this user 
	 */
	public boolean authenticate(String password) throws IOException{

		boolean ret = false;
		String user = (String)getTempValue(USER);


		if( user != null && password != null ) {
			String acct = (String)getTempValue(ACCT);
			if( acct != null ) {
				user = user+"@"+acct;
			}

			IPrincipal principal1 = getServer().authenticate(user,password.getBytes());

			if( principal1 != null ){
				setPrincipal(principal1);

				ret = true;
				String path = (String) principal1.getParameter(PARAMETER_ROOT);


				if( path != null) {
					FileSource dir = createNewFile(path);
					if( dir.isFile() ) {
						//  It MUST exists.
						// unique situation where we want to crate the directory in not exists
						throw new IOException("cannot set user root to a file ="+dir);							
					}
					if( !dir.exists()) {
						if( !dir.mkdirs()) {
							throw new IOException("cannot create user root ="+dir);
						}
					}

					setFtpRoot(dir);
				}

				path = (String) principal1.getParameter(PARAMETER_DEFAULT_DIRECTORY);

				if( path != null ) {
					FileSource dir = createNewFile(path);
					if( dir.isFile() ) {
						//  It MUST exists.
						// unique situation where we want to crate the directory in not exists
						throw new IOException("cannot set user directory to a file ="+dir);
					}
					if( !dir.exists()) {
						if( !dir.mkdirs()) {
							throw new IOException("cannot create user directory ="+dir);
						}
					}
					setCurrentDir(dir);
				}

			}
		}

		return ret;
	}


	/*
	 * Only creates files that are valid in the context of
	 * the ftpRoot.
	 * 1>  Path is converted from a relative path to absolute (if required)
	 * 2>  Check to ensure the flie is under the ftpRoot.
	 */
	public FileSource createNewFile(String path){
		FileSource ret = null;
		try {
			FileSource root = getFtpRoot();
			path = path.trim();
			int sz = path.length();
			boolean isRelative = sz==0 || (path.charAt(0) != '/' && (sz > 1 && path.charAt(1)!=':'));

			if( isRelative ){
				FileSource cwd = getCurrentDir();
				ret = cwd.getChild(path);				
			} else {
				if( path.startsWith("/")) {
					path = path.substring(1);
				}
				ret = root.getChild(path); 
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return ret;
	}

	public FileSource getCurrentDir() {
		return currentDir;
	}

	public void setCurrentDir(FileSource newDir) throws IOException {
		FileSource root = getFtpRoot();
		if( !root.isChildOfMine(newDir)) {
			throw new SecurityException("Invalid directory "+newDir);
		}
		if( !newDir.isDirectory() ) {
			throw new IllegalArgumentException("Illegal or non exesting directory "+newDir);
		}

		currentDir = newDir;

	}

	/*
	 * Generate a name that can be displayed without reveling the actual root Directory.
	 */
	public String getDisplayFileName(String name){
		String ret = name == null ? "/":name;

		if( ret.startsWith(rootName)){
			ret = ret.substring(rootNameLen);
			if( ret.length()==0 ) {
				ret = "/";
			} 
		}

		ret =  ret.replace('\\','/');
		if( ret.charAt(0)!= '/'){
			ret = "/"+ret;
		}

		return ret;
	}

	public Socket getDataSocket() {
		Socket ret = null;

		if( pasvSocket != null ){
			ret = pasvSocket.getDataSocket();
		} else {
			ret = dataSocket;
		}

		if( ret != null ){
			try {

				logDebug("Setting tmout for data Socket tmOut = "+activityTimeOut);
				ret.setSoTimeout(activityTimeOut);
			} catch (SocketException e) {
				e.printStackTrace();
			}
		}
		return ret;
	}

	/**
	 * Set the Data Socket.
	 * Primarily called from the PORT command.
	 * 
	 * @param dataSocket
	 */
	public void setDataSocket(Socket dataSocket) {	
		resetDataConnection();
		this.dataSocket = dataSocket;
	}

	public void setTempValue(String key, Object value){
		tempStorage.put(key,value);
	}

	public Object removeTempValue(String key){
		return tempStorage.remove(key);
	}

	// TODO: Move synchronized to parent project
	@Override
	public synchronized void reply(String text) throws IOException {
		super.reply(text);
	}

	@Override
	public synchronized void reply(int responseCode, String text) throws IOException {
		super.reply(responseCode, text);
	}

	public Object getTempValue(String key){
		return tempStorage.get(key);
	}

	/**
	 *  
	 * 
	 */
	public FtpRequestProcessor() {
		super();
		setCommandFactory(new FtpCommandFactory());
		setName("FtpRequestProcessor");
		setPropertyPrefix("FtpRequestProcessor");

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

	/* Process an FTP Request
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		try {

			/*
			 * Set the FTP root from the server.  This may be changed
			 * after authentication.
			 */   
			setFactory(((FtpServer)getServer()).getFileSourceFactory());
			setFtpRoot(((FtpServer)getServer()).getFtpRoot());
			reply(us.bringardner.net.ftp.FTP.REPLY_200_OK,getServer().getName()+" Ready");
			super.run();	

		} catch (Throwable e) {
			if (!((e instanceof SocketException) && e.toString().toLowerCase().contains("closed") )) {
				logError("An error occured Initializing client.  Closing the channel.",e);					
			}
			
		} 

	}





	public PassiveSocket getPasvSocket() {
		return pasvSocket;
	}

	private void resetDataConnection() {
		if( pasvSocket != null ) {
			pasvSocket.abor();
			pasvSocket = null;
		}

		if(dataSocket != null ) {
			try {
				dataSocket.close();
			} catch (IOException ex) {
			}
			dataSocket = null;
		}

	}
	/**
	 * Set the PassiveSocket (called by PASV command)
	 * @param passiveSocket
	 */
	public void setPasvSocket(PassiveSocket passiveSocket) {
		resetDataConnection();	
		pasvSocket = passiveSocket;
		if( pasvSocket == null ){
			passive = false;
		} else {
			passive = true;
		}
	}



	public FileSource getTmpFile() {
		return tmp;
	}

	public void setTmpFile(FileSource tmp) {
		this.tmp = tmp;
	}


	public boolean isAllowAnonymous() {
		return allowAnonymous;
	}

	public void setAllowAnonymous(boolean allowAnonymous) {
		this.allowAnonymous = allowAnonymous;
	}

	public int getBufferSize() {
		return ((FtpServer)getServer()).getBufferSize();
	}


	public int binaryStreamCopy(InputStream in, OutputStream out) throws IOException{
		byte buf[] = new byte[getBufferSize()];
		int bytes_read;
		int ret = 0;
		logDebug("Enter binaryStreamCopy");

		while( (bytes_read = in.read(buf)) >= 0 ){
			touch(); 
			if(bytes_read != 0) {
				out.write(buf,0,bytes_read);
				ret += bytes_read;
			}
		}
		logDebug("exit binaryStreamCopy ret="+ret);
		return ret;
	}

	/**
	 * 
	 */
	private void touch() {
		lastActivity = System.currentTimeMillis();
	}

	public int asciiStreamCopy(InputStream in, OutputStream out)	throws IOException{


		BufferedReader input = new BufferedReader(new InputStreamReader(in));
		PrintStream    output= new PrintStream(new BufferedOutputStream(out));


		String line;
		int ret = 0;

		while( (line = input.readLine()) != null ) {
			touch();
			output.println(line);
			ret += line.length() + 2;
		}
		output.flush();

		return ret;	
	}

	public void transferStream(InputStream in, OutputStream out, Socket sock) throws IOException{

		/*
		 * Start the transfer process then the go back and listen for commands.
		 * The TransferController will manage the reply.
		 * This give the client a chance to send ABOR command.
		 */

		FtpServerStream s = new FtpServerStream(this, in, out,sock);
		transferInProcess.start(s);

	}

	void setLinger(Socket sock) throws SocketException {
		int linger = getLinger();
		if( linger > 0 ) {
			logDebug("Set linger = "+linger);
			sock.setSoLinger(true, linger);
			sock.setSoTimeout((linger*1000)+1000);
		}


	}

	public void deliverStream(FileSource local, String ftpFileName) throws IOException{
		InputStream in = null;

		if( !local.exists() ) {
			reply(REPLY_450_FILE_ACTION_FAILED,"File does not exist, "+ftpFileName);
			return;
		}
		if( !local.canRead() ) {
			reply(REPLY_450_FILE_ACTION_FAILED,"Read access denied for "+ftpFileName);
			return;
		}

		try {
			in = local.getInputStream();
		} catch(Exception ex) {
			logError("Error creating stream",ex);
			reply(REPLY_450_FILE_ACTION_FAILED,"Can't create stream, "+ex);
			return;
		}

		Socket sock = getDataSocket();

		if( sock == null ){
			reply(REPLY_425_CANT_OPEN_DATA_CON,"Can't open data socket");
			return;
		} 

		OutputStream out = sock.getOutputStream();

		//  Check for a restart
		long skipped =0l;
		Long rest = (Long)removeTempValue(REST);
		if( rest != null ){
			long nb = rest.longValue();
			skipped = in.skip(nb); 
			logDebug("REST ="+rest+" skipped ="+skipped);
			if(skipped != nb) {
				reply(REPLY_450_FILE_ACTION_FAILED,"Can't skip "+nb+" bytes.  Skipped = "+skipped);
				return;
			}
		}
		transferStream(in, out, sock);


	}

	public int getLinger() {
		if( this.linger  ==  -2) {

			this.linger = -1;			
			String tmp = System.getProperty("JavaFtpServer.linger");
			if( tmp != null ) {
				try { this.linger = Integer.parseInt(tmp); } catch(Exception ex) {}
			} else {
				int tmo = getActivityTimeOut();
				if( tmo > 1000 ) {
					//  Linger is in seconds
					this.linger = tmo / 1000;
				}
			}
		}
		return this.linger;
	}

	public void setLinger(int linger) {
		this.linger = linger;
	}

	public void receiveStream(OutputStream out ) throws IOException{
		Socket sock = getDataSocket();

		if( sock == null ){
			reply(REPLY_425_CANT_OPEN_DATA_CON,"Can't open data socket");
		} else {
			InputStream in = sock.getInputStream();
			transferStream(in, out, sock);
		}
	}

	public boolean isImageMode() {
		return getRepresentationType() == TYPE_IMAGE;
	}

	public boolean isAsciiMode() {
		return getRepresentationType() == TYPE_ASCII;
	}

	public boolean isPassive() {
		return passive;
	}
	public void setPassive(boolean passive) {
		this.passive = passive;
	}

	@Override
	public boolean isSecure() {		
		return getServer().isSecure();
	}

	public int getRepresentationType() {
		return representationType;
	}
	public void setRepresentationType(int representationType) {
		this.representationType = representationType;
	}

	public int getActivityTimeOut() {
		return activityTimeOut;
	}
	public void setActivityTimeOut(int activityTimeOut) {
		this.activityTimeOut = activityTimeOut;
	}
	public FileSourceFactory getFactory() {
		return factory;
	}
	public void setFactory(FileSourceFactory factory) throws IOException {
		this.factory = factory;
		if( rootName != null ){
			setFtpRoot(factory.createFileSource(rootName));
		}
	}

	/**
	 * @return a new ServerSocketFactory
	 */
	public ServerSocketFactory getServerSocketFactory() {
		ServerSocketFactory ret = getServer().getServerSocketFactory(
				isChannelSecure() 
				// FileZilla won't work with this on and my client won'nt work with it off
				//TODO: what's going on	|| isSecure()
				);

		return ret;
	}

	public boolean isChannelSecure() {
		return channelSecure;
	}
	
	public void makeChannelSecure(String sslOrTsl) throws IOException {
		getConnection().negotiateSecureSocket(sslOrTsl);

		if( sslOrTsl == null ) {
			this.channelSecure = false;
		} else {
			this.channelSecure = true;
		}
	}

	public Socket createSocket(InetAddress addr, int port, InetAddress mine, int timeout) throws IOException{

		SocketFactory factory = getSocketFactory(); 
		//In FTP a socket connecting to a remote system is still a server for SSL/TLS

		if (factory instanceof SSLSocketFactory	) {
			SSLSocket ret = (SSLSocket) factory.createSocket();
			ret.setUseClientMode(false);
			InetSocketAddress sa = new InetSocketAddress(addr,port);
			ret.connect(sa, timeout);
			System.err.println("switch mode in processor");
			return ret;
		} else {
			System.err.println("not secure in processor");
			return factory.createSocket(addr, port, mine, timeout);
		}

	}


	/**
	 * @return the SocketFactory as configured for this Object.
	 */
	public SocketFactory getSocketFactory() {

		SocketFactory ret = getServer().getSocketFactory(isChannelSecure());

		return ret;
	}

	public int getPbsz() {
		return pbsz;
	}
	public void setPbsz(int pbsz) {
		this.pbsz = pbsz;
	}
	public String getProtLevel() {
		return protLevel;
	}
	public void setProtLevel(String protLevel) {
		this.protLevel = protLevel;
	}

	public void reset() {
		setPbsz(-1);
		setProtLevel(DATA_CHANNEL_PROTECTION_LEVEL_CLEAR);
		try {
			makeChannelSecure(null);
		} catch (Exception ex) {
			logError("Error in reset",ex);
		}
	}

	public long getLastActivity() {
		return lastActivity;
	}

	public int incLoginAttempts() {

		return ++loginAttempts;
	}

	/* (non-Javadoc)
	 * @see us.bringardner.net.framework.server.ICommandProcessor#translateResponseCode(int)
	 */
	public String translateResponseCode(int code) {
		//  Do we need to translate ???
		String ret = ""+code;
		return ret;
	}
	
	@Override
	protected ILogger getLogger(String name) {
		return super.getLogger("FtpRequestProcessor");
	}
}
