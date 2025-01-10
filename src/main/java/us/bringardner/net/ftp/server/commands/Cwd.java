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
 * ~version~V000.01.54-V000.01.37-V000.01.35-V000.01.30-V000.01.18-V000.01.08-V000.01.07-V000.01.05-V000.00.01-V000.00.00-
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
public class Cwd extends BaseCommand  implements FtpCommand {

	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public Cwd() {
		super(CWD);
	
	}

	/* 
	 * RFC 959
	 *  CHANGE WORKING DIRECTORY (CWD)

            This command allows the user to work with a different
            directory or dataset for file storage or retrieval without
            altering his login or accounting information.  Transfer
            parameters are similarly unchanged.  The argument is a
            pathname specifying a directory or other system dependent
            file group designator.

	 * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		
		if(!context.hasNext() ){			
			processor.reply(REPLY_450_FILE_ACTION_FAILED, "Missing path.\"" + processor.getCurrentDir()+"\" is the current directory");
			return;
		}

		//  can't use the context because file names may have spaces
		String path = context.getRemainingTokens();
		FileSource dir = processor.createNewFile(path);
		
		if(dir == null || !dir.isDirectory()) {
			//Not a valid path
			processor.reply(REPLY_450_FILE_ACTION_FAILED, "\"" + processor.getDisplayFileName(processor.getCurrentDir().getCanonicalPath())+"\" is the current directory");
		} else {			
				processor.setCurrentDir(dir);
				processor.reply(REPLY_250_FILE_ACTION_OK, "\"" +processor.getDisplayFileName(dir.getCanonicalPath())+"\" is the current directory");
		}

	}

	@Override
	public IPermission getPermission() {
		return READ_PERMISSION;
	}


}
