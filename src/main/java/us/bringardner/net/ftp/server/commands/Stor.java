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
 * ~version~V000.01.37-V000.01.35-V000.01.09-V000.01.07-V000.01.05-V000.01.02-V000.00.01-V000.00.00-
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
public class Stor  extends BaseCommand {

	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public Stor() {
		super(STOR);
	
	}

	/*
	 * RFC 959
	 * 
	 * STORE (STOR)

            This command causes the server-DTP to accept the data
            transferred via the data connection and to store the data as
            a file at the server site.  If the file specified in the
            pathname exists at the server site, then its contents shall
            be replaced by the data being transferred.  A new file is
            created at the server site if the file specified in the
            pathname does not alREAD_PERMISSIONy exist.


                  125, 150
                     (110)
                     226, 250
                     425, 426, 451, 551, 552
                  532, 450, 452, 553
                  500, 501, 421, 530


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
		if( target == null || target.isDirectory()){
			processor.reply(REPLY_450_FILE_ACTION_FAILED," Invalid or non existant name");
			return;
		} 
		processor.logDebug(getName()+" target = "+target.getAbsolutePath());

		Long rest = (Long)processor.removeTempValue(REST);
		// REST 0 means "no restart" (RFC 3659)
		boolean resume = rest != null && rest.longValue() > 0;
		if( resume ){
			long len = target.exists() ? target.length() : 0;
			if( len != rest.longValue()) {
				processor.reply(REPLY_450_FILE_ACTION_FAILED," Restart marker "+rest+" does not match the file size ("+len+")");
				return;
			}
		}
		/*
		 * receiveFile gets the data connection before touching the file and, unless
		 * this is a restart, writes to a temporary file that replaces the target only
		 * when the whole upload has been received.
		 */
		processor.receiveFile(target, resume);

	}
	
	@Override
	public IPermission getPermission() {
		return WRITE_PERMISSION;
	}


}
