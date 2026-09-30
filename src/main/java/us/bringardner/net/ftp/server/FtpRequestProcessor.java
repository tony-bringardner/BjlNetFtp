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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;

import us.bringardner.core.ILogger;
import us.bringardner.core.util.ThreadSafeDateFormat;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.net.framework.server.AbstractCommandProcessor;
import us.bringardner.net.framework.server.IPrincipal;
import us.bringardner.net.framework.server.IServer;
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
		 * Abort any running transfer without sending a reply (end of session).
		 */
		synchronized void abortQuietly() {
			if( stream.isActive() ) {
				stream.abort();
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
	/** The root's absolute path with "." and ".." resolved lexically (symbolic links NOT resolved) */
	private String rootAbsolute;
	private int rootNameLen = 0;
	private FileSource ftpRoot ;
	private FileSource currentDir ;
	private FileSourceFactory factory;
	private boolean allowAnonymous = true;
	private int representationType = TYPE_ASCII;


	private FileSource tmp;
	private transient PassiveSocket pasvSocket; 	
	private boolean passive = false;
	private transient Socket dataSocket;
	private long lastActivity = 0;

	//  Time out if inactive
	private int activityTimeOut = 10 * (60*1000);

	private boolean channelSecure = false;
	private int pbsz=-1;
	/** Data channel protection level set by PROT, null until PROT is sent (see isDataChannelSecure) */
	private String protLevel = null;
	private int loginAttempts=0;
	private int linger = -2;
	public transient StreamController transferInProcess = new StreamController();



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
		rootAbsolute = normalize(ftpRoot.getAbsolutePath());

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

			IPrincipal principal1 = getServer().authenticate(user,password.getBytes(java.nio.charset.StandardCharsets.UTF_8));

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


	/**
	 * Resolve a client supplied path to a file inside the user's FTP root.
	 * <p>
	 * The path is treated as a virtual path: absolute paths start at the user's root,
	 * relative paths start at the current directory, and "." / ".." are resolved lexically
	 * with ".." at the root staying at the root (chroot semantics). The result is then
	 * checked with canonical paths, which also rejects symbolic links that point outside
	 * the root.
	 * 
	 * @return the file, or null if the path is invalid or outside the user's root.
	 */
	public FileSource createNewFile(String path){
		if( path == null ) {
			return null;
		}
		try {
			FileSource root = getFtpRoot();
			path = path.trim();
			if( path.indexOf('\0') >= 0 ) {
				return null;
			}
			// Windows drive letters (C:...) are never valid virtual paths
			if( path.length() > 1 && path.charAt(1) == ':' ) {
				return null;
			}

			boolean isAbsolute = path.startsWith("/") || path.startsWith("\\");
			Deque<String> segments = new ArrayDeque<String>();
			if( !isAbsolute ) {
				// The client's view of the current directory (not its canonical path,
				// which is outside the root when the directory was reached through a link)
				String cwd = getVirtualPath(getCurrentDir());
				addSegments(segments, cwd);
			}
			addSegments(segments, path);

			if( segments.isEmpty() ) {
				return root;
			}
			StringBuilder rel = new StringBuilder();
			for(String seg : segments) {
				if( rel.length() > 0 ) {
					rel.append('/');
				}
				rel.append(seg);
			}
			FileSource ret = root.getChild(rel.toString());
			if( !isInsideRoot(ret) ) {
				logInfo("Rejected path outside of root: "+path);
				return null;
			}
			return ret;
		} catch (IOException e) {
			logError("Error resolving path "+path, e);
			return null;
		}
	}

	private static void addSegments(Deque<String> segments, String path) {
		for(String seg : path.split("[/\\\\]+")) {
			if( seg.isEmpty() || seg.equals(".")) {
				continue;
			}
			if( seg.equals("..")) {
				// ".." at the root stays at the root
				segments.pollLast();
			} else {
				segments.addLast(seg);
			}
		}
	}

	/**
	 * @return true if the client may use this file: it must be inside the user's root on
	 * the virtual (lexical) path, and, depending on {@link FtpServer.SymlinkPolicy}, where it
	 * really is (its canonical path, with symbolic links resolved) must be allowed too:
	 * <ul>
	 * <li>STRICT (default): the canonical path must be inside the root, so a link cannot
	 *     lead outside it.</li>
	 * <li>ALLOWED_TARGETS: the canonical path must be inside the root or inside one of
	 *     the server's allowedLinkTargets.</li>
	 * <li>FOLLOW: any link inside the root is followed.</li>
	 * </ul>
	 * In every mode ".." cannot leave the root, and the root itself may be a link.
	 */
	public boolean isInsideRoot(FileSource file) {
		if( file == null || rootName == null ) {
			return false;
		}
		try {
			// 1. Lexically inside the root (".." resolved, links not followed)
			String lexical = normalize(file.getAbsolutePath());
			if( relativeTo(lexical, rootAbsolute) == null && relativeTo(lexical, normalize(rootName)) == null ) {
				return false;
			}
			FtpServer.SymlinkPolicy policy = getSymlinkPolicy();
			if( policy == FtpServer.SymlinkPolicy.FOLLOW ) {
				return true;
			}
			// 2. Where it really is
			String canonical = normalize(file.getCanonicalPath());
			if( relativeTo(canonical, normalize(rootName)) != null ) {
				return true;
			}
			if( policy == FtpServer.SymlinkPolicy.ALLOWED_TARGETS ) {
				for(String target : allowedTargetsCanonical()) {
					if( relativeTo(canonical, target) != null ) {
						return true;
					}
				}
			}
			return false;
		} catch (IOException e) {
			logError("Can't resolve "+file, e);
			return false;
		}
	}

	private FtpServer.SymlinkPolicy getSymlinkPolicy() {
		IServer server = getServer();
		return server instanceof FtpServer ? ((FtpServer)server).getSymlinkPolicy() : FtpServer.SymlinkPolicy.STRICT;
	}

	private java.util.List<String> allowedTargetsCanonical() throws IOException {
		java.util.List<String> ret = new java.util.ArrayList<String>();
		IServer server = getServer();
		if( !(server instanceof FtpServer) ) {
			return ret;
		}
		for(String t : ((FtpServer)server).getAllowedLinkTargets()) {
			FileSource dir = getFactory().createFileSource(t);
			ret.add(normalize(dir.getCanonicalPath()));
		}
		return ret;
	}

	/**
	 * @return the path the client sees for this file ("/" is the user's root), built from
	 * its lexical path so a directory reached through a symbolic link shows as the link's
	 * name, never as the server's real path.
	 */
	public String getVirtualPath(FileSource file) {
		if( file == null ) {
			return "/";
		}
		String rel = relativeTo(normalize(file.getAbsolutePath()), rootAbsolute);
		if( rel == null && rootName != null ) {
			rel = relativeTo(normalize(file.getAbsolutePath()), normalize(rootName));
			if( rel == null ) {
				try {
					rel = relativeTo(normalize(file.getCanonicalPath()), normalize(rootName));
				} catch (IOException e) {
					rel = null;
				}
			}
		}
		if( rel == null ) {
			// Never show a path outside the root
			logInfo("No virtual path for "+file);
			return "/";
		}
		return "/"+rel;
	}

	/**
	 * Resolve "." and ".." lexically and use "/" separators. Keeps a leading "/" or drive.
	 */
	static String normalize(String path) {
		if( path == null ) {
			return null;
		}
		String p = path.replace('\\', '/');
		String prefix = "";
		if( p.startsWith("//") ) {
			prefix = "//"; // UNC
			p = p.substring(2);
		} else if( p.startsWith("/") ) {
			prefix = "/";
			p = p.substring(1);
		} else if( p.length() > 1 && p.charAt(1) == ':' ) {
			prefix = p.substring(0, 2)+"/";
			p = p.length() > 3 ? p.substring(3) : "";
		}
		Deque<String> segs = new ArrayDeque<String>();
		addSegments(segs, p);
		return prefix + String.join("/", segs);
	}

	/**
	 * @return path relative to root (without a leading "/", "" for the root itself),
	 * or null if path is not root or below it. Compares whole path elements.
	 */
	static String relativeTo(String path, String root) {
		if( path == null || root == null ) {
			return null;
		}
		if( path.equals(root) ) {
			return "";
		}
		String prefix = root.endsWith("/") ? root : root+"/";
		return path.startsWith(prefix) ? path.substring(prefix.length()) : null;
	}

	public FileSource getCurrentDir() {
		return currentDir;
	}

	public void setCurrentDir(FileSource newDir) throws IOException {
		if( !isInsideRoot(newDir)) {
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
			
		} finally {
			/*
			 * RFC 959: an unexpected close on the control connection has the effect of an ABOR.
			 * Release any passive listener / data socket and stop a running transfer so no
			 * ports, threads or files are left open after the session ends.
			 */
			try {
				transferInProcess.abortQuietly();
			} catch (Throwable e) {
				logDebug("Error aborting transfer at end of session", e);
			}
			resetDataConnection();
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


	/**
	 * 
	 */
	private void touch() {
		lastActivity = System.currentTimeMillis();
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
				skipped = skipFully(in, nb); 
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

	/**
	 * InputStream.skip() may legitimately skip fewer bytes than requested,
	 * so keep skipping until done or end of stream.
	 * @return number of bytes actually skipped
	 */
	public static long skipFully(InputStream in, long n) throws IOException {
		long total = 0;
		while( total < n ) {
			long s = in.skip(n - total);
			if( s > 0 ) {
				total += s;
			} else {
				// skip() returned 0: check for end of stream
				if( in.read() < 0 ) {
					break;
				}
				total++;
			}
		}
		return total;
	}

	public int getLinger() {
		if( this.linger  ==  -2) {

			this.linger = -1;			
			/*
			 * SO_LINGER is off by default. With linger on, close() blocks the transfer thread
			 * until the peer acknowledges all data, up to the linger time (this used to default
			 * to the activity timeout, i.e. 10 minutes). A normal close still delivers all
			 * queued data in the background.
			 */
			String tmp = System.getProperty("JavaFtpServer.linger");
			if( tmp != null ) {
				try { this.linger = Integer.parseInt(tmp); } catch(Exception ex) {}
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
		return getServer().getServerSocketFactory(isDataChannelSecure());
	}

	/**
	 * RFC 4217 data channel protection:
	 * <ul>
	 * <li>PROT P: data connections use TLS.</li>
	 * <li>PROT C: data connections are clear.</li>
	 * <li>No PROT: clear after AUTH TLS (the RFC default), TLS for implicit TLS
	 *     (the control connection was TLS from the start).</li>
	 * </ul>
	 * Previously this followed only "AUTH was done", so PROT C was ignored and implicit
	 * TLS used clear data connections that no TLS client could talk to.
	 */
	public boolean isDataChannelSecure() {
		String level = protLevel;
		if( level == null ) {
			return isSecure();
		}
		return DATA_CHANNEL_PROTECTION_LEVEL_PRIVATE.equals(level) && (isSecure() || isChannelSecure());
	}

	/**
	 * @return true if the control connection is protected (implicit TLS or AUTH).
	 */
	public boolean isControlChannelSecure() {
		return isSecure() || isChannelSecure();
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

	/**
	 * Open an active mode (PORT/EPRT) data connection to the client.
	 * 
	 * @param addr client address
	 * @param port client port
	 * @param mine local address to bind (the control connection's local address), may be null
	 * @param timeout connect timeout in milliseconds
	 */
	public Socket createSocket(InetAddress addr, int port, InetAddress mine, int timeout) throws IOException{

		SocketFactory factory = getSocketFactory(); 
		// Create unconnected so we can apply a connect timeout.
		// (Previously the timeout was passed as the LOCAL PORT argument of
		// createSocket(addr, port, localAddr, localPort), which tried to bind port 10.)
		Socket ret = factory.createSocket();
		try {
			if (ret instanceof SSLSocket	) {
				SSLSocket ssl = (SSLSocket) ret;
				//In FTP a socket connecting to a remote system is still a server for SSL/TLS
				ssl.setUseClientMode(false);
				String force = System.getProperty(us.bringardner.core.SecureBaseObject.PROPERTY_FORCE_TLS_VERSION);
				if( force != null && !force.trim().isEmpty()) {
					ssl.setEnabledProtocols(new String[] {force.trim()});
				}
			}
			if( mine != null ) {
				ret.bind(new InetSocketAddress(mine, 0));
			}
			ret.connect(new InetSocketAddress(addr,port), timeout);
			ret.setSoTimeout(getActivityTimeOut());
			return ret;
		} catch (IOException e) {
			try {
				ret.close();
			} catch (IOException e1) {
			}
			throw e;
		}
	}

	/**
	 * RFC 2577 checks for an active mode (PORT/EPRT) target.
	 * 
	 * @return null if the target is allowed, otherwise the reason it is not.
	 */
	public String checkActiveTarget(InetAddress addr, int port) {
		if( ((FtpServer)getServer()).isAllowForeignDataAddress() ) {
			return null;
		}
		InetAddress peer = getConnection().getSocket().getInetAddress();
		if( !isSameHost(addr, peer) ) {
			return "Data connection address must match the client address";
		}
		if( port < 1024 ) {
			return "Data connection port must be 1024 or higher";
		}
		return null;
	}

	/**
	 * @return true if a passive data connection from this address should be accepted.
	 */
	public boolean isAllowedPassivePeer(InetAddress addr) {
		if( ((FtpServer)getServer()).isAllowForeignDataAddress() ) {
			return true;
		}
		return isSameHost(addr, getConnection().getSocket().getInetAddress());
	}

	static boolean isSameHost(InetAddress a, InetAddress b) {
		if( a == null || b == null ) {
			return false;
		}
		return java.util.Arrays.equals(normalize(a), normalize(b));
	}

	// IPv4-mapped IPv6 (::ffff:a.b.c.d) compares equal to the IPv4 address
	private static byte[] normalize(InetAddress a) {
		byte [] b = a.getAddress();
		if( b.length == 16 ) {
			boolean mapped = true;
			for(int i=0; i < 10 && mapped; i++ ) {
				mapped = b[i] == 0;
			}
			if( mapped && (b[10]&0xff) == 0xff && (b[11]&0xff) == 0xff ) {
				return new byte[] {b[12],b[13],b[14],b[15]};
			}
		}
		return b;
	}

	/**
	 * @return the timeout (ms) for opening an active mode data connection.
	 */
	public int getConnectTimeout() {
		return ((FtpServer)getServer()).getConnectTimeout();
	}


	/**
	 * @return the SocketFactory as configured for this Object.
	 */
	public SocketFactory getSocketFactory() {

		SocketFactory ret = getServer().getSocketFactory(isDataChannelSecure());

		return ret;
	}

	public int getPbsz() {
		return pbsz;
	}
	public void setPbsz(int pbsz) {
		this.pbsz = pbsz;
	}
	/**
	 * @return the PROT level in effect: the one sent by the client, or the default
	 * (P for implicit TLS, C otherwise).
	 */
	public String getProtLevel() {
		String level = protLevel;
		if( level == null ) {
			return isSecure() ? DATA_CHANNEL_PROTECTION_LEVEL_PRIVATE : DATA_CHANNEL_PROTECTION_LEVEL_CLEAR;
		}
		return level;
	}
	public void setProtLevel(String protLevel) {
		this.protLevel = protLevel;
	}

	public void reset() {
		setPbsz(-1);
		setProtLevel(null);
		try {
			makeChannelSecure(null);
		} catch (Exception ex) {
			logError("Error in reset",ex);
		}
	}

	public long getLastActivity() {
		return lastActivity;
	}

	/**
	 * Wait before replying to a failed login (see {@link FtpServer#setLoginFailureDelay(int)}).
	 */
	public void loginFailedDelay() {
		int delay = ((FtpServer)getServer()).getLoginFailureDelay();
		if( delay > 0 ) {
			try {
				Thread.sleep(delay);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
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
