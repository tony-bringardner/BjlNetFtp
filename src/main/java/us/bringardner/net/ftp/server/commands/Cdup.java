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
 * ~version~V000.01.37-V000.01.35-V000.01.07-V000.01.05-V000.00.01-V000.00.00-
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
public class Cdup extends BaseCommand implements FtpCommand {

	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public Cdup() {
		super(CDUP);
	
	}

	/*
	 * RFC 959                                                     October 1985
	 *  CHANGE TO PARENT DIRECTORY (CDUP)

            This command is a special case of CWD, and is included to
            simplify the implementation of programs for transferring
            directory trees between operating systems having different

            syntaxes for naming the parent directory.  The reply codes
            shall be identical to the reply codes of CWD.  See
            Appendix II for further details.


	 * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		FileSource cur = processor.getCurrentDir();
		FileSource parent = cur.getParentFile();
		FileSource top = processor.getFtpRoot();
		
		if( parent != null && parent.getCanonicalPath().startsWith(top.getCanonicalPath())){
			processor.setCurrentDir(parent);
			String display = processor.getDisplayFileName(processor.getCurrentDir().getCanonicalPath());
			processor.reply(REPLY_250_FILE_ACTION_OK,"Current dir is "+display);
		} else {
			processor.reply(REPLY_450_FILE_ACTION_FAILED,"Parrent is not availible");
		}
	}

	@Override
	public IPermission getPermission() {
		return READ_PERMISSION;
	}

}
