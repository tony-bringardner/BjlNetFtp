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
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;

import us.bringardner.core.BaseObject;
import us.bringardner.net.ftp.FTP;
import us.bringardner.net.ftp.server.commands.List;
import us.bringardner.net.ftp.server.commands.Mlst;



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
	private FtpClientFile parentFile;

	
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

	/**
	 * Local helper function to clean up the entry 
	 * by removing unwanted spaces.  This just makes it 
	 * easier to parser the entry.
	 *  
	 * @param entry received from remote system
	 * @return entry with unwanted spaces removed.
	 */
	private String cleanup(String entry) {

		StringBuffer ret = new StringBuffer(entry.length());
		byte [] data = entry.getBytes();
		byte lst = '\0';
		/*  /services/home/thewallicks.com/Backup/marie/My Documents/JFS
		 * 
		 * There are 9 data sections in an entry separated by whitespace.
		 * the last one is the name but it could contain whitespace.
		 * So, we want to stop before we change the name.
		 */
		int section=0;
		int idx=0;
		for (; section < 8 && idx < data.length; idx++) {
			if((lst=data[idx]) == ' ') {
				section++;
				while((data[idx+1]==' ' || data[idx+1]=='\t') && idx < data.length ) {
					idx++;
				}

			}
			ret.append((char)lst);
		}

		//  We've found the name so use it to set our field
		name = entry.substring(idx).trim();

		return ret.toString();
	}

	private void parseEntry(String entry) throws IOException {

		if(client.isMlstSupported() ){
			parseMlstEntry(entry);
		} else {
			parseUnixEntry(entry);
		}

	}

	private void parseMlstEntry(String entry) {
		String [] parts = entry.split(";");
		if( parts.length < 4 ) {
			//  Can't be a valid MLST entry
			parseUnixEntry(entry);
			return;
		}
		name = parts[parts.length-1].trim();
		if( name.length()>0 && name.charAt(0)=='/') {
			name = name.substring(1);
		}

		for (int idx = 0,sz=parts.length-1; idx < sz; idx++) {
			String [] tmp = parts[idx].split("=");
			String fact = tmp[0].trim().toUpperCase();
			if( fact.equals(FTP.MODIFY)) {
				/*
				 *    Symbolically, a time-val may be viewed as
				 *
				 * YYYYMMDDHHMMSS.sss
				 *
				 * The "." and subsequent digits ("sss") are optional.  However the "."
				 * MUST NOT appear unless at least one following digit also appears.
				 * 
				 */
				try {
					String time = tmp[1];
					if( tmp[1].indexOf('.') < 0 ) {
						time = time+".000";
					}
					lastModified = Mlst.TIME_FORMAT.parse(time).getTime();					
				} catch (ParseException e) {
					logError("Can't parse time",e);					
				}
			} else if( fact.equals(FTP.PERM)) {
				if( tmp.length > 1) {
					mlstPermissions = tmp[1].toLowerCase();
				}
			} else if( fact.equals(FTP.SIZE)) {
				length = Long.parseLong(tmp[1]);
			} else if( fact.equals(FTP.TYPE)) {
				if(tmp[1].equalsIgnoreCase("file")) {
					type = TYPE_FILE;
				} else {
					type = TYPE_DIR;
				}
			}
		}

	}



	private void parseUnixEntry(String entry) {
		//  perms   links owner       group  size  mm  dd hh:mm name   
		//drwxrwxrwx   4 QSYS           0    51200 Feb  9 21:28 home
		//-rw-------   1 peter                848  Dec 14 11:22 00README.txt
		//2> validate format
		// ??  How ??
		//1> Eliminate any double spaces in the text
		//int permPos = 0;
		//int linksPos = 1;
		int ownerPos = 2;
		int groupPos = 3;
		int sizePos = 4;
		int monthPos = 5;
		int dayPos = 6;
		int timePos = 7;

		/*
		 * Cleanup will remove filler spaces and set the name 
		 */
		entry = cleanup(entry.trim());

		type = entry.charAt(0);
		permissions = entry.substring(1,10).toCharArray();
		String [] parts = entry.split(" ");
		owner = parts[ownerPos];
		group = parts[groupPos];

		length = Long.parseLong(parts[sizePos]);

		String tmp = parts[monthPos].trim()+" "+parts[dayPos].trim()+" "+parts[timePos].trim();
		

		try {
			lastModified = List.oldDateFmt.parse(tmp).getTime();
		} catch (Exception e) {
			try {
				lastModified = List.newDateFmt.parse(tmp).getTime();
			} catch (Exception e2) {
				Calendar cal = Calendar.getInstance();
				tmp += " "+cal.get(Calendar.YEAR);
				try {
					lastModified = List.newDateFmt.parse(tmp).getTime();
				} catch (Exception e3) {
					logError("Can't parse date / time val ='"+tmp+"' entry="+entry);
				}
			}
		}				

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
