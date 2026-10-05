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
package us.bringardner.net.ftp.client;

import java.util.Locale;
import java.util.function.Consumer;

import us.bringardner.net.ftp.FTP;
import us.bringardner.net.ftp.server.commands.List;
import us.bringardner.net.ftp.server.commands.Mlst;

/**
 * One parsed entry of a directory listing: an MLSx line (RFC 3659) or a Unix style LIST line
 * ("-rw-r--r--   1 owner group  1234 Oct  1 12:25 name").
 * <p>
 * Shared by {@link FtpClientFile} and bjl_file_system_ftp's FtpFile, so both read listings the
 * same way (they used to have their own copies, and the file system's still had a bug fixed here).
 * Immutable.
 */
public final class ListEntry {

	/** {@link #getType()} of a directory */
	public static final char TYPE_DIR = 'd';
	/** {@link #getType()} of a file */
	public static final char TYPE_FILE = '-';

	private String name;
	private String owner;
	private String group;
	private long length;
	private long lastModified;
	private char type;
	private char[] permissions;
	private String mlstPermissions;

	private ListEntry() {
	}

	/**
	 * @param entry a line of a listing
	 * @param mlsx true if it is an MLST / MLSD line (an entry that isn't a valid MLSx line is read
	 *  as a Unix LIST line)
	 * @param problems told about a date or time that can't be read (the entry is still returned,
	 *  with lastModified 0); may be null
	 * @return the parsed entry
	 * @throws RuntimeException (IndexOutOfBounds, NumberFormat) for a LIST line that isn't in the Unix format
	 */
	public static ListEntry parse(String entry, boolean mlsx, Consumer<String> problems) {
		ListEntry ret = new ListEntry();
		Consumer<String> report = problems != null ? problems : msg -> { };
		if( mlsx ) {
			ret.parseMlstEntry(entry, report);
		} else {
			ret.parseUnixEntry(entry, report);
		}
		return ret;
	}

	/**
	 * Split an MLSx entry into its facts and pathname (RFC 3659 section 7.2): the facts each
	 * end with ';', then one space, then the pathname, which may itself contain spaces or ';'.
	 * Servers used to be read as if the entry ended with a bare name; RFC 3659 servers (and
	 * this project's server since BJL-48) send the whole pathname for MLST.
	 * @param entry an MLST or MLSD line (a leading space is allowed)
	 * @return { facts, pathname }, or null if the line isn't an MLSx entry
	 */
	public static String[] splitMlsxEntry(String entry) {
		if( entry == null ) {
			return null;
		}
		int start = 0;
		while( start < entry.length() && entry.charAt(start) == ' ' ) {
			start++;
		}
		// The facts end at the first "; ": a fact value can't contain ';' but may contain a
		// space (a Windows owner such as "NT AUTHORITY\SYSTEM")
		int end = entry.indexOf("; ", start);
		if( end < 0 ) {
			return null;
		}
		return new String[] { entry.substring(start, end+1), entry.substring(end+2) };
	}

	/**
	 * @param pathname the pathname from an MLSx entry: a name (MLSD) or a whole path (MLST)
	 * @return the last part of it, the file's name
	 */
	public static String mlsxName(String pathname) {
		String p = pathname;
		while( p.length() > 1 && p.endsWith("/") ) {
			p = p.substring(0, p.length()-1);
		}
		int idx = p.lastIndexOf('/');
		return idx >= 0 ? p.substring(idx+1) : p;
	}

	/**
	 * Remove the filler spaces between the fields of a LIST line, and take the name (which may
	 * contain spaces) from the end of it.
	 *
	 * @param entry received from remote system
	 * @return entry with unwanted spaces removed (up to the name)
	 */
	private String cleanup(String entry) {

		StringBuilder ret = new StringBuilder(entry.length());
		// Work on chars: the old byte-based version used a UTF-8 byte index as a String
		// index, which garbled names when owner/group contained non-ASCII characters.
		char [] data = entry.toCharArray();
		char lst;
		/*
		 * There are 9 data sections in an entry separated by whitespace.
		 * the last one is the name but it could contain whitespace.
		 * So, we want to stop before we change the name.
		 */
		int section=0;
		int idx=0;
		for (; section < 8 && idx < data.length; idx++) {
			if((lst=data[idx]) == ' ') {
				section++;
				while( idx+1 < data.length && (data[idx+1]==' ' || data[idx+1]=='\t')) {
					idx++;
				}

			}
			ret.append(lst);
		}

		//  We've found the name so use it to set our field
		name = entry.substring(idx).trim();

		return ret.toString();
	}

	private void parseMlstEntry(String entry, Consumer<String> problems) {
		// RFC 3659 section 7.2: facts (each ending with ';'), one space, then the pathname.
		// MLST gives the whole pathname (/dir/a.txt), MLSD usually just the name (BJL-49).
		String [] split = splitMlsxEntry(entry);
		String [] parts = split == null ? new String[0] : split[0].split(";");
		if( parts.length < 3 ) {
			//  Can't be a valid MLST entry
			parseUnixEntry(entry, problems);
			return;
		}
		name = mlsxName(split[1]);

		for (int idx = 0,sz=parts.length; idx < sz; idx++) {
			String [] tmp = parts[idx].split("=");
			String fact = tmp[0].trim().toUpperCase(Locale.ROOT);
			if( fact.equals(FTP.MODIFY)) {
				/*
				 *    Symbolically, a time-val may be viewed as
				 *
				 * YYYYMMDDHHMMSS.sss
				 *
				 * The "." and subsequent digits ("sss") are optional.  However the "."
				 * MUST NOT appear unless at least one following digit also appears.
				 */
				try {
					lastModified = Mlst.parseTime(tmp[1]);
				} catch (java.time.DateTimeException e) {
					problems.accept("Can't parse time "+tmp[1]+" ("+e.getMessage()+")");
				}
			} else if( fact.equals(FTP.PERM)) {
				if( tmp.length > 1) {
					mlstPermissions = tmp[1].toLowerCase(Locale.ROOT);
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

	private void parseUnixEntry(String entry, Consumer<String> problems) {
		//  perms   links owner       group  size  mm  dd hh:mm name
		//drwxrwxrwx   4 QSYS           0    51200 Feb  9 21:28 home
		//-rw-------   1 peter                848  Dec 14 11:22 00README.txt
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

		// ls style dates, the right year for recent entries ("Oct  1 12:25" has no year) and
		// English month names (BJL-45, BJL-46), shared with the server
		try {
			lastModified = List.parseListDate(parts[monthPos], parts[dayPos], parts[timePos], java.time.ZoneId.systemDefault(), System.currentTimeMillis());
		} catch (java.time.DateTimeException e) {
			problems.accept("Can't parse date / time val ='"+parts[monthPos]+" "+parts[dayPos]+" "+parts[timePos]+"' entry="+entry);
		}
	}

	/** @return the file's name (the last part of an MLST path name) */
	public String getName() {
		return name;
	}

	/** @return the owner from a LIST line, null for an MLSx line */
	public String getOwner() {
		return owner;
	}

	/** @return the group from a LIST line, null for an MLSx line */
	public String getGroup() {
		return group;
	}

	/** @return the size in bytes */
	public long getLength() {
		return length;
	}

	/** @return the modification time (ms since the epoch), 0 if it wasn't given or couldn't be read */
	public long getLastModified() {
		return lastModified;
	}

	/** @return {@link #TYPE_DIR}, {@link #TYPE_FILE}, or the type character of a LIST line ('l' for a link, say) */
	public char getType() {
		return type;
	}

	/** @return the 9 permission characters of a LIST line ("rwxr-xr-x"), null for an MLSx line */
	public char[] getPermissions() {
		return permissions == null ? null : permissions.clone();
	}

	/** @return the MLSx perm fact, lower case (e.g. "adfrw"), null if there wasn't one */
	public String getMlstPermissions() {
		return mlstPermissions;
	}
}
