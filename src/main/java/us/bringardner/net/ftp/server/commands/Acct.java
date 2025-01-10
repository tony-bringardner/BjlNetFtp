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
 * ~version~V000.01.35-V000.01.05-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.net.ftp.server.commands;

import java.io.IOException;

import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FtpRequestProcessor;

/**
 * @author Tony Bringardner
 *
 */
public class Acct  extends NoAuthReqBaseCommand {

	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public Acct() {
		super(ACCT);
	
	}

	/* 
	 * RFC 959
	 * ACCOUNT (ACCT)

            The argument field is a Telnet string identifying the user's
            account.  The command is not necessarily related to the USER
            command, as some sites may require an account for login and
            others only for specific access, such as storing files.  In
            the latter case the command may arrive at any time.

            There are reply codes to differentiate these cases for the
            automation: when account information is required for login,
            the response to a successful PASSword command is reply code
            332.  On the other hand, if account information is NOT
            required for login, the reply to a successful PASSword
            command is 230; and if the account information is needed for
            a command issued later in the dialogue, the server should
            return a 332 or 532 reply depending on whether it stores
            (pending receipt of the ACCounT command) or discards the
            command, respectively.


	Valid responses for ACCT
                  230
                  202
                  530
                  500, 501, 503, 421
	 * 
	 * 
	 * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		String commandLine = context.getCommandLine();
		
		String [] args = commandLine.split(" ");

		if( args.length != 2 ) {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Not enough parameters");
		}
		
		processor.setTempValue(ACCT, args[1]);
		
		if(  processor.authenticate((String) processor.getTempValue(PASS)) ){
			processor.reply(REPLY_230_USER_LOGGED_IN,"user authenticated");
		} else {
			processor.reply(REPLY_530_USER_NOT_LOGGED_IN,"user not authenticated");
		}
		
	}

}
