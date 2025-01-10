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
 * ~version~V000.01.37-V000.01.35-V000.01.05-V000.01.01-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.net.ftp.server.commands;


import java.io.IOException;

import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FtpCommand;
import us.bringardner.net.ftp.server.FtpRequestProcessor;


/**
 * @author Tony Bringardner
 *
 */
public class Debug extends BaseCommand implements FtpCommand {

	private static final long serialVersionUID = 1L;


	/**
	 * 
	 */
	public Debug() {
		super(DEBUG);
	}

	
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		
		
		boolean all = false;
		if(context.hasNext()) {
			String val = context.getNextToken();
			if(val.toLowerCase().equals("all")) {
				all = true;
			} else if(val.toLowerCase().equals("none")) {
				all = false;
			}
		}
		
		//  Toggle the debug flag
		processor.setDebug(!processor.isDebug());

		if( all ) {
			//  Set the server to match this processor.  
			//  This will impact processors as they are created by this server.
			processor.getServer().setDebug(processor.isDebug());
		}
		
		
		processor.reply(REPLY_250_FILE_ACTION_OK,"Debug="+processor.isDebug()+" Server Debug="+processor.getServer().isDebug());
		
	}
	
	@Override
	public IPermission getPermission() {
		return WRITE_PERMISSION;
	}


}
