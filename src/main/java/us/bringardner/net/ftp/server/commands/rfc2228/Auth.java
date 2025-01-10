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
 * ~version~V000.01.53-V000.01.35-V000.01.05-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.net.ftp.server.commands.rfc2228;

import java.io.IOException;

import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FtpRequestProcessor;
import us.bringardner.net.ftp.server.commands.NoAuthReqBaseCommand;

/**
 * @author Tony Bringardner
 *
 */
public class Auth extends NoAuthReqBaseCommand {

	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public Auth() {
		super(AUTH);
	
	}

	/*
 RFC 2228                FTP Security Extensions             October 1997


   AUTHENTICATION/SECURITY MECHANISM (AUTH)

      The argument field is a Telnet string identifying a supported
      mechanism.  This string is case-insensitive.  Values must be
      registered with the IANA, except that values beginning with "X-"
      are reserved for local use.

      If the server does not recognize the AUTH command, it must respond
      with reply code 500.  This is intended to encompass the large
      deployed base of non-security-aware ftp servers, which will
      respond with reply code 500 to any unrecognized command.  If the
      server does recognize the AUTH command but does not implement the
      security extensions, it should respond with reply code 502.

      If the server does not understand the named security mechanism, it
      should respond with reply code 504.

      If the server is not willing to accept the named security
      mechanism, it should respond with reply code 534.

      If the server is not able to accept the named security mechanism,
      such as if a required resource is unavailable, it should respond
      with reply code 431.

      If the server is willing to accept the named security mechanism,
      but requires security data, it must respond with reply code 334.

      If the server is willing to accept the named security mechanism,
      and does not require any security data, it must respond with reply
      code 234.

      If the server is responding with a 334 reply code, it may include
      security data as described in the next section.

      Some servers will allow the AUTH command to be reissued in order
      to establish new authentication.  The AUTH command, if accepted,
      removes any state associated with prior FTP Security commands.
      The server must also require that the user reauthorize (that is,
      reissue some or all of the USER, PASS, and ACCT commands) in this
      case (see section 4 for an explanation of "authorize" in this
      context).
      
      AUTH
            234
            334
            502, 504, 534, 431
            500, 501, 421


	 * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		String commandLine = context.getCommandLine();
		String [] args = commandLine.split(" ");
		processor.reset();
		
		if( args.length != 2 ) {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM," Wrong number of parameters.  Expected 1, got "+(args.length-1));
			return;
		}
		String mech = args[1];
		if( !(mech.equals("SSL") || mech.equals("TLS") ) ){
			processor.reply(REPLY_504_NOT_IMP_FOR_PARAM," Unsupported secutity mechanizm, "+mech);
			return;
		}
		
		try {
			
			processor.reply(REPLY_234_SECURITY_DATA_EXCHANGE_COMPLETE,getName()+" Accepted");
			processor.makeChannelSecure(mech);
		} catch (Exception ex) {
			//processor.logError("Can't negotiate a secure channel.",ex);
			processor.reply(REPLY_431_NEED_UNAVILIBLE_RESOURCE_TO_PROCESS," Can't negotiate a secure channel. ("+ex+")");
		} 


	}

}
