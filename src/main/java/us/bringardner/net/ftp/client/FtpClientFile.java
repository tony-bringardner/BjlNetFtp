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
 * ~version~V000.01.55-V000.01.45-V000.01.35-V000.01.15-V000.01.02-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2006
 *
 */
package us.bringardner.net.ftp.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import us.bringardner.core.BaseObject;
import us.bringardner.net.ftp.FTP;



public class FtpClientFile extends BaseObject {

	public static final char TYPE_DIR = 'd';
	public static final char TYPE_FILE = '-';
	//public static final ThreadSafeDateFormat YOUNG_DATE_FORMAT = new ThreadSafeDateFormat("MMM dd HH:mm yyyy");
	//public static final ThreadSafeDateFormat OLD_DATE_FORMAT = new ThreadSafeDateFormat("MMM dd yyyy");

	//public static final ThreadSafeDateFormat MLST_DATE_FORMAT = new ThreadSafeDateFormat("yyyyMMddHHmmSS.sss");
	//public static final ThreadSafeDateFormat MLST_SHORT_DATE_FORMAT = new ThreadSafeDateFormat("yyyyMMddHHmmSS");

	private String listEntry;
	private String parent;
	private FtpClient client;
	private String name;
	private String owner;
	private String group;
	private long length;
	private long lastModified;
	private char type;
	private char[] permissions;
	private String mlstPermissions;
	// volatile: lazily created with double-checked locking in getParentFile()
	private volatile FtpClientFile parentFile;

	
	/**
	 * Only used from command line processor and probably not working
	 * @param listEntry
	 * @param client
	 * @throws IOException
	 */
	public FtpClientFile(String listEntry, FtpClient client) throws IOException {
		this.client = client;
		this.listEntry = listEntry;
		parseEntry(listEntry);
		//parent = client.executePwd();
	}


	/**
	 * Only used from command line processor
	 * @param dirPath
	 * @param listEntry
	 * @param client
	 * @throws IOException
	 */
	public FtpClientFile(String dirPath, String listEntry, FtpClient client) throws IOException {
		this.client = client;
		this.listEntry = listEntry;
		this.parent = dirPath.trim();

		if( listEntry != null && !listEntry.isEmpty()) {
			parseEntry(listEntry);
		} else {
			//  Assume this is a directory
			this.type = TYPE_DIR;
			int idx = dirPath.lastIndexOf(FtpClient.SEPERATOR_CHAR);
			if(idx >= 0){
				this.parent = dirPath.substring(0,idx);
				this.name = dirPath.substring(idx+1);
			} else {
				this.parent = "";
				this.name = dirPath;

			}
		}
	}

	public FtpClientFile(FtpClient client) {
		this.client = client;
	}
	
	@Override
	public void logDebug(String msg) {client.logDebug(msg);}
	@Override
	public void logDebug(String msg, Throwable error) {
		client.logDebug(msg, error);
	}
	
	@Override
	public void logError(String msg) {
		client.logError(msg);
	}
	
	@Override
	public void logError(String msg, Throwable error) {
		client.logError(msg, error);
	}
	
	@Override
	public void logInfo(String msg) {
		client.logInfo(msg);
	}
	
	@Override
	public void logInfo(String msg, Throwable error) {
		client.logInfo(msg, error);
	}
	
	@Override
	public boolean isDebugEnabled() {
		return client.isDebugEnabled();
	}
	
	@Override
	public boolean isErrorEnabled() {
		return client.isErrorEnabled();
	}
	@Override
	public boolean isInfoEnabled() {
		return client.isInfoEnabled();
	}
	
	public String toString() {
		String ret = null;

		if(isDirectory()) {
			ret = getAbsolutePath()+" Directory";
		} else {
			ret = getAbsolutePath()+" "+getLength()+" "+(new Date(getLastModified()));
		}
		return ret;
	}


	public String getListEntry() {
		return listEntry;
	}

	public String getParent() {
		return parent;
	}

	public FtpClientFile getParetFile() {
		if( parentFile == null && name.length()>0 && !name.equals(FtpClient.SEPERATOR)) {
			synchronized (this) {
				if( parentFile == null ) {
					int idx = parent.lastIndexOf(FtpClient.SEPERATOR_CHAR);
					if( idx > 0 ) {
						String pp = parent.substring(0,idx);
						String pn = parent.substring(idx+1);
						parentFile = new FtpClientFile(client);
						parentFile.listEntry = "";
						parentFile.name = pn;                        
						parentFile.parent = pp;
						parentFile.type = TYPE_DIR;

						/*
						 * These are probably wrong but FTP does not
						 * provide a way to get information about a directory 
						 * without listing every file in the parents parent.
						 */
						parentFile.owner = owner;
						parentFile.permissions = permissions;
						parentFile.lastModified = lastModified;
					}

				}
			}

		}

		return parentFile;
	}

	private void parseEntry(String entry) throws IOException {
		//  The parsing is shared with bjl_file_system_ftp's FtpFile, see ListEntry
		ListEntry e = ListEntry.parse(entry, client.isMlstSupported(), this::logError);
		name = e.getName();
		owner = e.getOwner();
		group = e.getGroup();
		length = e.getLength();
		lastModified = e.getLastModified();
		type = e.getType();
		permissions = e.getPermissions();
		mlstPermissions = e.getMlstPermissions();
	}

	/**
	 * @see ListEntry#splitMlsxEntry(String)
	 * @deprecated moved to {@link ListEntry#splitMlsxEntry(String)}
	 */
	@Deprecated
	public static String[] splitMlsxEntry(String entry) {
		return ListEntry.splitMlsxEntry(entry);
	}

	/**
	 * @see ListEntry#mlsxName(String)
	 * @deprecated moved to {@link ListEntry#mlsxName(String)}
	 */
	@Deprecated
	public static String mlsxName(String pathname) {
		return ListEntry.mlsxName(pathname);
	}

	public boolean isDirectory() {
		return (type == TYPE_DIR);
	}

	public boolean isFile () {
		return !isDirectory();
	}

	public long getLastModified() {
		return lastModified;
	}

	public long getLength() {
		return length;
	}

	public String getName() {
		return name;
	}

	public String getOwner() {
		return owner;
	}
	
	public String getGroup() {
		return group;
	}

	public char[] getPermissions() {
		return permissions;
	}

	public InputStream getInputStream() throws IOException {
		return getInputStream(false);
	}

	public InputStream getInputStream(long startingPos) throws IOException {
		return getInputStream(false, startingPos);
	}

	public InputStream getInputStream(boolean ascii) throws IOException {
		if( !isFile() ) {
			throw new IOException("Can't create stream from directory");
		}
		return client.getInputStream(getParent()+FtpClient.SEPERATOR+name, ascii);
	}

	public InputStream getInputStream(boolean ascii, long startingPos) throws IOException {
		if( !isFile() ) {
			throw new IOException("Can't create stream from directory");
		}
		return client.getInputStream(getParent()+FtpClient.SEPERATOR+name, ascii, startingPos);
	}

	public OutputStream getOutputStream(boolean ascii, boolean append) throws IOException {
		if( !isFile() ) {
			throw new IOException("Can't create stream from directory");
		}
		return client.getOutputStream(getParent()+FtpClient.SEPERATOR+name, ascii, append);
	}

	public String getAbsolutePath() {
		String p = getParent();
		String nm = getName();
		String ret = null;
		if( p.equals("/")) {
			ret = FtpClient.SEPERATOR+nm;
		} else {
			ret = p+FtpClient.SEPERATOR+nm;
		}
		
		
		return ret;
	}

	public boolean delete() throws IOException {

		return client.delete(getAbsolutePath());
	}

	public boolean canRead() {
		boolean ret = false;
		if( mlstPermissions != null ) {
			// mlst permissions are more complicated but more accurate.
			if(isDirectory()) {
				ret = mlstPermissions.indexOf('l') >= 0;
			} else {
				ret = mlstPermissions.indexOf('r') >= 0;
			}
		} else   if(permissions != null && permissions.length > 0) {
			ret = permissions[0] == 'r';
		}
		return ret;
	}

	public boolean canWrite() {
		boolean ret = false;
		if( mlstPermissions != null ) {
			// mlst permissions are more complicated but more accurate.
			if(isDirectory()) {
				ret = mlstPermissions.indexOf('c') >= 0;
			} else {
				ret = mlstPermissions.indexOf('w') >= 0;
			}        	
		} else   if(permissions != null && permissions.length > 0) {
			ret = permissions[0] == 'w';
		}
		return ret;
	}

	public OutputStream getOutputStream() throws IOException {
		return getOutputStream(false, false);
	}

	public boolean mkdir() throws IOException {
		boolean ret = client.mkDir(getAbsolutePath());
		return ret;
	}

	public boolean mkdirs() throws IOException {
		boolean ret = client.mkDirs(getAbsolutePath());
		return ret;
	}

	public boolean renameTo(String newAbsolutePath) throws IOException {

		return client.rename(getAbsolutePath(), newAbsolutePath);
	}

	public OutputStream getAppendOutputStream() throws IOException {

		return getOutputStream(false, false);
	}

	/**
	 * This is not supported by standard Ftp.
	 * However, us.bringardner.net.ftp.server.Server supports a 'SITE' command
	 * that allows us to do it.
	 * 
	 * @param lastModifiedTime
	 * @see us.bringardner.net.ftp.server.FtpServer
	 */
	public void setLastModified(long lastModifiedTime) {

		try {
			ClientFtpResponse res = client.executeCommand(FTP.SITE,"modDate "+lastModifiedTime+" "+getAbsolutePath());

			if( res._getResponseCode() == FTP.REPLY_213_FILE_STATUS) {
				this.lastModified = lastModifiedTime;
			}
		} catch (IOException e) {
			logError("Error setting modDate",e);
		}
	}

	public void dereferenceChildern() {
		// Nothing to do but probably should either here or in FtpFileSource.
		
	}




}
