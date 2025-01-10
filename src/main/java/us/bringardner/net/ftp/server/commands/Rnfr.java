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
 * ~version~V000.01.37-V000.01.35-V000.01.07-V000.01.05-V000.01.02-V000.00.01-V000.00.00-
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
public class Rnfr  extends BaseCommand implements FtpCommand {

	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public Rnfr() {
		super(RNFR);
	
	}

	/* 
	 * RFC 959
	 * RENAME FROM (RNFR)

            This command specifies the old pathname of the file which is
            to be renamed.  This command must be immediately followed by
            a "rename to" command specifying the new file pathname.
     *
	 * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		String commandLine = context.getCommandLine();
		
		int idx = commandLine.indexOf(' ');
		if( idx > 0 ){
			commandLine = commandLine.substring(idx);
		} else {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Not enough parameters");
			return;
		}
			
		FileSource target = processor.createNewFile(commandLine);
		commandLine = processor.getDisplayFileName(target.toString());
		
		if( !target.exists()){
			processor.reply(REPLY_450_FILE_ACTION_FAILED," Invalid or non existant name");
			return;
		} 

		if(target.equals(processor.getFtpRoot())) {
			processor.reply(REPLY_450_FILE_ACTION_FAILED," Invalid or non existant name");
			return;
		}
		
		
		if( target.exists() ) {
			processor.setTmpFile(target);
			processor.reply(REPLY_350_FILE_ACTION_PENDING,"OK send new name");
		} else {
			processor.reply(REPLY_450_FILE_ACTION_FAILED,commandLine+" does not exist");
		}

		

	}
	
	@Override
	public IPermission getPermission() {
		return WRITE_PERMISSION;
	}


}
