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
 * ~version~V000.01.41-V000.01.35-V000.01.05-V000.00.01-V000.00.00-
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
 * @author Tony Bringardner
 *
 */
public class AuthError extends NoAuthReqBaseCommand {

	private static final long serialVersionUID = 1L;
	public static final String AUTH_ERROR = "AuthError";

	/**
	 * 
	 */
	public AuthError() {
		super(AUTH_ERROR);
	}

	/* This command is issued if an unrecognized command is Received after
	 * the user is authenticated.
	 * 
	 * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(ICommandProcessor processor, IRequestContext command) throws IOException {
		processor.reply(REPLY_502_NOT_IMPLEMENTED,"Command not implemented for \""+command+"\"");
	}

	/* (non-Javadoc)
	 * @see us.bringardner.net.ftp.server.commands.BaseCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, us.bringardner.net.framework.server.IRequestContext)
	 */
	@Override
	public void execute(FtpRequestProcessor processor, IRequestContext context)
			throws IOException {
		// Not implemented
		
	}

	


}
