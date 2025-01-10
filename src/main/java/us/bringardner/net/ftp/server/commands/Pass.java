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
 * ~version~V000.01.37-V000.01.35-V000.01.05-V000.00.01-V000.00.00-
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
public class Pass extends NoAuthReqBaseCommand {

	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public Pass() {
		super(PASS);
	
	}

	/*
	 * RFC 959
	 * PASSWORD (PASS)

            The argument field is a Telnet string specifying the user's
            password.  This command must be immediately preceded by the
            user name command, and, for some sites, completes the user's
            identification for access control.  Since password
            information is quite sensitive, it is desirable in general
            to "mask" it or suppress typeout.  It appears that the
            server has no foolproof way to achieve this.  It is
            therefore the responsibility of the user-FTP process to hide
            the sensitive password information.

 

	 * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		if(!context.hasNext()) {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Not enough parameters");
		}
		
			
			try {
				String pw = context.getNextToken();
				if(  processor.authenticate(pw) ){
					processor.reply(REPLY_230_USER_LOGGED_IN,"password ok");
				} else {
					//  The password was not accepted so I'll assume account info is required
					processor.setTempValue(PASS, pw);
					processor.reply(REPLY_332_NEED_ACCOUNT,"user not logged in, try adding account");
                    if( processor.incLoginAttempts() > 3 ) {
                        processor.stop();
                    }
				}
			} catch(Exception ex) {
				//  May throw security 
				processor.reply(REPLY_530_USER_NOT_LOGGED_IN,"not logged in err="+ex);
			}
	}
}
