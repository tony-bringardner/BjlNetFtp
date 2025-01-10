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
 * ~version~V000.01.54-V000.01.37-V000.01.35-V000.01.08-V000.01.07-V000.01.05-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.net.ftp.server.commands;


import java.io.IOException;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FtpCommand;
import us.bringardner.net.ftp.server.FtpRequestProcessor;


/**
 * @author Tony Bringardner
 *
 */
public class Dele extends BaseCommand implements FtpCommand {

	private static final long serialVersionUID = 1L;


	/**
	 * 
	 */
	public Dele() {
		super(DELE);
	
	}

	
	/* 
	 * RFC 959
	 * 
         DELETE (DELE)

            This command causes the file specified in the pathname to be
            deleted at the server site.  If an extra level of protection
            is desired (such as the query, "Do you really wish to
            delete?"), it should be provided by the user-FTP process.

	 *
	 *  @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		if(!context.hasNext() ){
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Expected file or dir name");
			return;
		}
		
		//  can't use the context because file names may have spaces
		String name = context.getRemainingTokens();
		
		FileSource dest = processor.createNewFile(name);
		FileSource cwd = processor.getCurrentDir();
			 
		if(dest == null 				
				 || !dest.exists()) {
			processor.reply(REPLY_450_FILE_ACTION_FAILED,"Invalid or non existing file");
		} else {			
			name = processor.getDisplayFileName(dest.toString());
			if( dest.delete() ) {
				if(cwd.equals(dest)) {
					// we just deleted the cwd
					processor.setCurrentDir(cwd.getParentFile());
				}
				processor.reply(REPLY_250_FILE_ACTION_OK,name+" deleted");
				
			} else {
				processor.reply(REPLY_450_FILE_ACTION_FAILED,name+" can't be deleted");
			}
		}
	}
	
	@Override
	public IPermission getPermission() {
		return WRITE_PERMISSION;
	}

}
