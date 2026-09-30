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
			FtpServerStream current = stream;
			synchronized (current.lock) {
				if( !current.isActive()) {
					reply(us.bringardner.net.ftp.FTP.REPLY_226_CLOSING_DATA_CON,"No transfer in progress. ABOR command successful");
				} else {
					// The transfer thread sends 426 followed by 226
					current.abort();
				}
			}
		}

		/**
		 * @return true if a data transfer has been started and has not sent its final reply.
		 */
		public synchronized boolean isTransferInProgress() {
			return stream.isActive();
		}

		/**
		 * Send the preliminary reply and start the transfer.
		 * <p>
		 * The 150 reply MUST be sent before the transfer thread is started. Otherwise a small
		 * transfer can complete, and send its 226, before the 150 is written, and the client
		 * sees the replies out of order.
		 * 
		 * @return true if the transfer was started, false if it was refused (a 425 has been sent).
		 */
		public synchronized boolean start(FtpServerStream next, String message) throws IOException {
			if(stream.isActive()) {
				next.discard();
				reply(REPLY_425_CANT_OPEN_DATA_CON, "Data transfer already in process");
				return false;
			}
			reply(REPLY_150_FILE_STATUS_OK, message);
			stream = next;
			next.start();
			return true;
		}

		public boolean start(FtpServerStream next) throws IOException {
			return start(next, "Opening "+(next.processor.isAsciiMode() ? "ASCII":"BINARY")+" mode data connection");
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

		if( ret != null && ret.isClosed() ) {
			// A data connection is used for one transfer only. If the previous transfer
			// closed it, the client must send a new PASV/PORT first.
			logDebug("Data socket is already closed");
			ret = null;
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
		transferStream(in, out, sock, false, null);
	}

	/**
	 * Start the transfer process then the go back and listen for commands.
	 * The transfer thread sends the final reply.
	 * This give the client a chance to send ABOR command.
	 * 
	 * @param upload true if data flows from the socket to local storage (STOR/APPE)
	 * @param handler optional callback run after the copy completes (may be null)
	 */
	public void transferStream(InputStream in, OutputStream out, Socket sock, boolean upload, FtpServerStream.CompletionHandler handler) throws IOException{
		FtpServerStream s = new FtpServerStream(this, in, out, sock, upload, handler);
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

		boolean started = false;
		try {
			Socket sock = getDataSocket();

			if( sock == null ){
				reply(REPLY_425_CANT_OPEN_DATA_CON,"Can't open data socket");
				return;
			} 

			OutputStream out;
			try {
				out = sock.getOutputStream();
			} catch (IOException e) {
				reply(REPLY_425_CANT_OPEN_DATA_CON,"Can't open data connection: "+e.getMessage());
				return;
			}

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
			transferStream(in, out, sock, false, null);
			// From here on the transfer (or StreamController on refusal) owns the stream
			started = true;
		} finally {
			if( !started ) {
				try {
					in.close();
				} catch (IOException e) {
				}
			}
		}
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
			try {
				out.close();
			} catch (IOException e) {
			}
			reply(REPLY_425_CANT_OPEN_DATA_CON,"Can't open data socket");
		} else {
			InputStream in;
			try {
				in = sock.getInputStream();
			} catch (IOException e) {
				try {
					out.close();
				} catch (IOException e1) {
				}
				reply(REPLY_425_CANT_OPEN_DATA_CON,"Can't open data connection: "+e.getMessage());
				return;
			}
			transferStream(in, out, sock, true, null);
		}
	}

	/** Suffix used for the temporary file that receives a STOR upload. */
	public static final String UPLOAD_TEMP_SUFFIX = ".ftp-part";
	/** Suffix used for the previous version of a file while an upload is moved into place. */
	public static final String UPLOAD_BACKUP_SUFFIX = ".ftp-old";

	/**
	 * Receive a file (STOR/APPE) without putting the existing file at risk.
	 * <ol>
	 * <li>The data connection is obtained <b>before</b> anything is opened, so a STOR with no
	 * data connection gets a 425 and leaves the file untouched.</li>
	 * <li>When {@code append} is false the data is written to a hidden temporary file in the
	 * same directory and renamed over the target only after the whole upload has arrived.
	 * An aborted, timed out or failed upload leaves the original untouched and the temporary
	 * file is deleted.</li>
	 * <li>When {@code append} is true (APPE, or STOR after REST) data is appended in place,
	 * which is what a resumed upload needs.</li>
	 * </ol>
	 * @param target file to create or replace
	 * @param append true to append to the target in place
	 */
	public void receiveFile(final FileSource target, boolean append) throws IOException {
		Socket sock = getDataSocket();
		if( sock == null ){
			reply(REPLY_425_CANT_OPEN_DATA_CON,"Can't open data socket");
			return;
		}

		InputStream in;
		try {
			in = sock.getInputStream();
		} catch (IOException e) {
			reply(REPLY_425_CANT_OPEN_DATA_CON,"Can't open data connection: "+e.getMessage());
			return;
		}

		OutputStream out = null;
		FtpServerStream.CompletionHandler handler = null;
		try {
			if( append ) {
				out = target.getOutputStream(true);
			} else {
				final FileSource temp = siblingOf(target, UPLOAD_TEMP_SUFFIX);
				out = temp.getOutputStream(false);
				handler = new FtpServerStream.CompletionHandler() {
					@Override
					public void transferComplete(boolean success) throws IOException {
						if( success ) {
							commitUpload(temp, target);
						} else if( temp.exists() && !temp.delete() ) {
							logError("Can't delete incomplete upload "+temp);
						}
					}
				};
			}
		} catch (Exception e) {
			logError("Can't open "+target+" for writing", e);
			try {
				sock.close();
			} catch (IOException e1) {
			}
			reply(REPLY_553_FILE_NAME_NOT_ALLOWED,"Can't write "+getDisplayFileName(target.getName())+": "+e.getMessage());
			return;
		}

		transferStream(in, out, sock, true, handler);
	}

	/**
	 * Move a completed upload into place, replacing target.
	 */
	void commitUpload(FileSource temp, FileSource target) throws IOException {
		// On POSIX file systems this atomically replaces the target.
		if( temp.renameTo(target) ) {
			return;
		}
		// Some file systems (Windows, some FileSource implementations) won't rename over an
		// existing file. Move the old one aside first so it can be restored on failure.
		FileSource backup = null;
		if( target.exists() ) {
			backup = siblingOf(target, UPLOAD_BACKUP_SUFFIX);
			if( !target.renameTo(backup) ) {
				temp.delete();
				throw new IOException("Can't replace "+target.getName());
			}
		}
		if( !temp.renameTo(target) ) {
			if( backup != null ) {
				backup.renameTo(target);
			}
			temp.delete();
			throw new IOException("Can't move upload into place for "+target.getName());
		}
		if( backup != null && !backup.delete() ) {
			logError("Can't delete backup "+backup);
		}
	}

	private static FileSource siblingOf(FileSource target, String suffix) throws IOException {
		FileSource dir = target.getParentFile();
		String name = "."+target.getName()+"."+Long.toHexString(System.nanoTime())+suffix;
		return dir.getChild(name);
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
