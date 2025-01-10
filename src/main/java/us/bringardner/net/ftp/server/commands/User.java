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
 * ~version~V000.01.56-V000.01.37-V000.01.35-V000.01.05-V000.00.01-V000.00.00-
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
public class User  extends NoAuthReqBaseCommand {

	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public User() {
		super(USER);
	
	}

	/* 
	 * RFC 959
	 * USER NAME (USER)

            The argument field is a Telnet string identifying the user.
            The user identification is that which is required by the
            server for access to its file system.  This command will
            normally be the first command transmitted by the user after
            the control connections are made (some servers may require
            this).  Additional identification information in the form of
            a password and/or an account command may also be required by
            some servers.  Servers may allow a new USER command to be
            entered at any point in order to change the access control
            and/or accounting information.  This has the effect of
            flushing any user, password, and account information alREAD_PERMISSIONy
            supplied and beginning the login sequence again.  All
            transfer parameters are unchanged and any file transfer in
            progress is completed under the old access control
            parameters.



	 * 
	 * 
	 * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		//String fst = context.getFirstToken();
		
		if(! context.hasNext()) {			
				processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Not enough parameters");				
		}
		
				
		String user = context.getNextToken();
		if( context.hasNext()) {
			String nxt = context.getNextToken();
			System.out.println("nxt="+nxt);
		}
		if( user.equalsIgnoreCase(FTP_USER) || user.equalsIgnoreCase(ANONYMOUS) ) {
			if(!processor.isAllowAnonymous()){
				processor.reply(REPLY_550_ACTION_NOT_TAKEN,"Anonymous login is not supported.  Please use a valid user ID.");
			} else {
				processor.setTempValue(USER,user);
				processor.reply(REPLY_331_USER_NAME_OK_NEED_PASS,"Anonymous login use email address as password");
			}
		} else {
			processor.setTempValue(USER,user);
			processor.reply(REPLY_331_USER_NAME_OK_NEED_PASS,"give password for "+user);
		}

	}

}
