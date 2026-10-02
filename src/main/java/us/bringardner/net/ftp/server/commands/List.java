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
 * ~version~V000.01.45-V000.01.37-V000.01.35-V000.01.17-V000.01.07-V000.01.05-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.net.ftp.server.commands;

import java.io.IOException;
import java.net.Socket;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import us.bringardner.io.filesource.FileSource;

import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FtpCommand;
import us.bringardner.net.ftp.server.FtpRequestProcessor;

/**
 * @author Tony Bringardner
 *
 */
public class List  extends BaseCommand {

	private static final long serialVersionUID = 1L;
	// long arithmetic: the int version overflowed to about 17 days
	public static final long ONE_YEAR = 365L*24*60*60*1000;
	/** Half of an average Gregorian year, the "recent" limit ls uses (POSIX, GNU ls) */
	public static final long SIX_MONTHS = 31556952000L/2;
	/*
	 * LIST dates are written the way ls writes them (BJL-45): English month names whatever
	 * the server's locale, and the day padded with a space, not a zero ("Oct  1"). Files
	 * changed within the last six months show the time, older (and future) files the year,
	 * so a client can always tell which year a time belongs to. DateTimeFormatter
	 * is immutable, so sessions don't wait on each other (SimpleDateFormat needed a lock).
	 */
	/** "Oct  1 12:25": files changed within the last six months */
	public static final DateTimeFormatter RECENT_FORMAT = DateTimeFormatter.ofPattern("MMM ppd HH:mm", Locale.US);
	/** "Dec 14  2024": older or future files (the year is right aligned under the time) */
	public static final DateTimeFormatter OLD_FORMAT = DateTimeFormatter.ofPattern("MMM ppd  yyyy", Locale.US);
	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM", Locale.US);

	/**
	 * @param time the file's last modified time
	 * @param now the current time
	 * @param zone the time zone to show the time in
	 * @return the date as LIST shows it, e.g. "Oct  1 12:25" or "Dec 14  2024"
	 */
	public static String formatListDate(long time, long now, ZoneId zone) {
		LocalDateTime t = LocalDateTime.ofInstant(Instant.ofEpochMilli(time), zone);
		long age = now - time;
		return (age >= 0 && age < SIX_MONTHS ? RECENT_FORMAT : OLD_FORMAT).format(t);
	}

	/**
	 * Parse the date of an ls style LIST entry, the reverse of {@link #formatListDate}.
	 * A recent entry ("Oct  1 12:25") has no year, so it gets the most recent year that
	 * doesn't put it more than a day in the future (a day allows for time zones and clock
	 * differences).
	 * @param month English month abbreviation ("Oct")
	 * @param day day of the month ("1" or "01")
	 * @param timeOrYear "HH:mm" for recent entries, the year for older ones
	 * @param zone the time zone the listing is in
	 * @param now the current time
	 * @return the time in milliseconds
	 * @throws DateTimeException if the values aren't a valid date
	 */
	public static long parseListDate(String month, String day, String timeOrYear, ZoneId zone, long now) throws DateTimeException {
		Month m = Month.from(MONTH.parse(capitalize(month.trim())));
		int d;
		try {
			d = Integer.parseInt(day.trim());
		} catch (NumberFormatException e) {
			throw new DateTimeParseException("Invalid day", day, 0, e);
		}
		String ty = timeOrYear.trim();
		if( ty.indexOf(':') < 0 ) {
			int year;
			try {
				year = Integer.parseInt(ty);
			} catch (NumberFormatException e) {
				throw new DateTimeParseException("Invalid year", ty, 0, e);
			}
			return LocalDateTime.of(year, m, d, 0, 0).atZone(zone).toInstant().toEpochMilli();
		}
		LocalTime time = LocalTime.parse(ty.length() == 4 ? "0"+ty : ty);
		long latest = now + 24L*60*60*1000;
		int year = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone).getYear() + 1;
		DateTimeException last = null;
		// Feb 29 may need to go back a few years
		for (int tries = 0; tries < 9; tries++, year--) {
			try {
				long ret = LocalDateTime.of(year, m, d, time.getHour(), time.getMinute()).atZone(zone).toInstant().toEpochMilli();
				if( ret <= latest ) {
					return ret;
				}
			} catch (DateTimeException e) {
				last = e;
			}
		}
		throw last != null ? last : new DateTimeException("Invalid date "+month+" "+day+" "+timeOrYear);
	}

	private static String capitalize(String s) {
		return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.US)+s.substring(1).toLowerCase(Locale.US);
	}
	
	/**
	 * 
	 */
	public List() {
		super(LIST);
	
	}

	/* 
	 *  RFC 959
	 * LIST (LIST)

            This command causes a list to be sent from the server to the
            passive DTP.  If the pathname specifies a directory or other
            group of files, the server should transfer a list of files
            in the specified directory.  If the pathname specifies a
            file then the server should send current information on the
            file.  A null argument implies the user's current working or
            default directory.  The data transfer is over the data
            connection in type ASCII or type EBCDIC.  (The user must
            ensure that the TYPE is appropriately ASCII or EBCDIC).
            Since the information on a file may vary widely from system
            to system, this information may be hard to use automatically
            in a program, but may be quite useful to a human user.

	 * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		
		FileSource dir = null;
		
	
		if(context.hasNext() ){
			dir = processor.createNewFile(context.getRemainingTokens());
		} else {
			dir = processor.getCurrentDir();
		} 
			
		
		if( dir == null || !dir.exists()){
			processor.reply(REPLY_450_FILE_ACTION_FAILED," Invalid or non existant name");
			return;
		} 
		
		FileSource [] list = null;
		
		if( dir.isDirectory()) {
			if( (list = dir.listFiles()) == null ) {
				list = new FileSource[0];
			}
		} else {
			list = new FileSource[1];
			list[0] = dir;
		}
		
		
		Socket sock = processor.getDataSocket();
		if( sock == null ) {
			processor.reply(REPLY_425_CANT_OPEN_DATA_CON,"Can't get a data socket");
		} else {
			sendListing(processor, sock, list, this::formatFile, "Opening ASCII mode data connection for file list", false);
		}
	}
	
	/*
	 -r-xr-xr-x   1 owner    group           16024 Sep  4 13:25 FTPserver.java
	 dr-xr-xr-x   1 owner    group               0 Sep  4 13:29 tstDir
	 format like this
	 */
	
	public String formatFile(FileSource file) throws IOException{

		String ret = "";
		String dt = formatListDate(file.lastModified(), System.currentTimeMillis(), ZoneId.systemDefault());

		String perm = 
				(file.canOwnerRead() ? "r":"-")
				+(file.canOwnerWrite() ? "w":"-")
				+(file.canOwnerExecute() ? "x":"-")
				
				+(file.canGroupRead() ? "r":"-")
				+(file.canGroupWrite() ? "w":"-")
				+(file.canGroupExecute() ? "x":"-")
				
				+(file.canOtherRead() ? "r":"-")
				+(file.canOtherWrite() ? "w":"-")
				+(file.canOtherExecute() ? "x":"-")
				
				;
		
		ret = (file.isDirectory() ? "d":"-")+perm+
				"   1 "+file.getOwner()+"  "+file.getGroup()+" "+
				pad(""+file.length(),26)+" "+
				dt+" "+
				file.getName()
				;

		return ret;

	}

	private String pad(String val, int sz)
	{
		String ret = val;
		if( val.length() < sz ) {
			ret = ("                              "+val);
			ret = ret.substring(ret.length()-sz);
		}

		return ret;
	}

	@Override
	public IPermission getPermission() {
		return READ_PERMISSION;
	}
}
