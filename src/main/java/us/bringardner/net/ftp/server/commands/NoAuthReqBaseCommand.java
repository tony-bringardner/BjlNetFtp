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
 * ~version~V000.01.37-V000.01.35-V000.01.25-V000.01.05-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.net.ftp.server.commands;
import java.io.IOException;

import us.bringardner.net.framework.server.ICommandProcessor;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FtpRequestProcessor;


/**
 * USe the base class for commands that do not require autherization
 * (help, user, pass,...)
 * @author Tony Bringardner
 *
 */
public abstract class NoAuthReqBaseCommand extends BaseCommand {

	
	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public NoAuthReqBaseCommand(String command) {
		super(command);		
	}

	@Override
	public boolean requiresAuthorization() {
		return false;
	}
	
	/*
	 * No auth required for this command
	 * 
	 * @see us.bringardner.net.ftp.server.FtpCommand#process(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(ICommandProcessor processor, IRequestContext context) throws IOException {		
		execute((FtpRequestProcessor)processor,context);		
	}
	
	 public boolean isAuthorized(ICommandProcessor processor,IRequestContext context) {
		return true;
	}

	@Override
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		// Do nothing
		
	}

	 
	 

}
