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
 * ~version~V000.01.35-V000.01.07-V000.01.05-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.net.ftp.server.commands;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FtpCommand;
import us.bringardner.net.ftp.server.FtpRequestProcessor;

/**
 * @author Tony Bringardner
 *
 */
public class Nlst extends BaseCommand  implements FtpCommand {

	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public Nlst() {
		super(NLST);
	
	}

	/* 
	 * RFC 959
	 * NAME LIST (NLST)
	 * 
	 * This command causes a directory listing to be sent from
            server to user site.  The pathname should specify a
            directory or other system-specific file group descriptor; a
            null argument implies the current directory.  The server
            will return a stream of names of files and no other
            information.  The data will be transferred in ASCII or
            EBCDIC type over the data connection as valid pathname
            strings separated by <CRLF> or <NL>.  (Again the user must
            ensure that the TYPE is correct.)  This command is intended
            to return information that can be used by a program to
            further process the files automatically.  For example, in
            the implementation of a "multiple get" function.

	 * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		String commandLine = context.getCommandLine();
		FileSource dir = null;
		int idx = commandLine.indexOf(' ');
		if( idx > 0 ){
			commandLine = commandLine.substring(idx);
			dir = processor.createNewFile(commandLine);
		} else {
			dir = processor.getCurrentDir();
		} 		
		
		if( dir == null || !dir.isDirectory()){
			processor.reply(REPLY_450_FILE_ACTION_FAILED," Invalid or non existant name");
			return;
		} 
		
		FileSource [] list = dir.listFiles();
		
		Socket sock = processor.getDataSocket();
		
		if( sock == null ){
			processor.reply(REPLY_425_CANT_OPEN_DATA_CON,"Can't open data socket");
		} else {
			
			PrintWriter list_out = new PrintWriter(sock.getOutputStream(),true);				
			processor.reply(REPLY_150_FILE_STATUS_OK,"Opening ASCII mode data connection for file list");
			for(int i = 0; i < list.length ; i ++)	{
				list_out.println(list[i].getName());
			}
			list_out.flush();
			list_out.close();
			sock.close();
			processor.reply(REPLY_226_CLOSING_DATA_CON,"Transfer complete");
		}

	}

}
