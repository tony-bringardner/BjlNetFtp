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
import java.util.Date;

import us.bringardner.io.filesource.FileSource;

import us.bringardner.core.util.ThreadSafeDateFormat;
import us.bringardner.io.CRLFLineWriter;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FtpCommand;
import us.bringardner.net.ftp.server.FtpRequestProcessor;

/**
 * @author Tony Bringardner
 *
 */
public class List  extends BaseCommand  implements FtpCommand {

	private static final long serialVersionUID = 1L;
	public static final long ONE_YEAR = (1000*60)*60*24*365;
	public static final ThreadSafeDateFormat newDateFmt = new ThreadSafeDateFormat("MMM dd HH:mm");
	public static final ThreadSafeDateFormat oldDateFmt = new ThreadSafeDateFormat("MMM dd yyyy");
    //public static final ThreadSafeDateFormat completeFmt = new ThreadSafeDateFormat("MMM dd yyyy HH:mm");
	
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
		CRLFLineWriter list_out = new CRLFLineWriter(sock.getOutputStream());
		processor.reply(REPLY_150_FILE_STATUS_OK,"Opening ASCII mode data connection for file list");
		for(int i = 0; i < list.length ; i ++) {
			list_out.writeLine(formatFile(list[i]));						
		}
	
		list_out.flush();
		list_out.close();
		processor.reply(REPLY_226_CLOSING_DATA_CON,"Transfer complete");
	}
	}
	
	/*
	 -r-xr-xr-x   1 owner    group           16024 Sep  4 13:25 FTPserver.java
	 dr-xr-xr-x   1 owner    group               0 Sep  4 13:29 tstDir
	 format like this
	 */
	
	public String formatFile(FileSource file) throws IOException{

		String ret = "";
		String dt = null;

		long tm = file.lastModified();
        
        
		if( System.currentTimeMillis() - tm  < ONE_YEAR ) {
			dt = newDateFmt.format(new Date(tm));
		} else {
			dt = oldDateFmt.format(new Date(tm));
		}
        
		
        //dt = completeFmt.format(new Date(tm));
        
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
