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
package us.bringardner.net.ftp.server.commands.rfc2228;

import java.io.IOException;

import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FtpRequestProcessor;
import us.bringardner.net.ftp.server.commands.NoAuthReqBaseCommand;

/**
 * @author Tony Bringardner
 *
 */
public class Ccc extends NoAuthReqBaseCommand {

	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public Ccc() {
		super(CCC);
	
	}

	/*
 RFC 2228                FTP Security Extensions             October 1997

CLEAR COMMAND CHANNEL (CCC)

      This command does not take an argument.
      
      It is desirable in some environments to use a security mechanism
      to authenticate and/or authorize the client and server, but not to
      perform any integrity checking on the subsequent commands.  This
      might be used in an environment where IP security is in place,
      insuring that the hosts are authenticated and that TCP streams
      cannot be tampered, but where user authentication is desired.

      If unprotected commands are allowed on any connection, then an
      attacker could insert a command on the control stream, and the
      server would have no way to know that it was invalid.  In order to
      prevent such attacks, once a security data exchange completes
      successfully, if the security mechanism supports integrity, then
      integrity (via the MIC or ENC command, and 631 or 632 reply) must
      be used, until the CCC command is issued to enable non-integrity
      protected control channel messages.  The CCC command itself must
      be integrity protected.

      Once the CCC command completes successfully, if a command is not
      protected, then the reply to that command must also not be
      protected.  This is to support interoperability with clients which
      do not support protection once the CCC command has been issued.

      This command must be preceded by a successful security data
      exchange.

      If the command is not integrity-protected, the server must respond
      with a 533 reply code.

      If the server is not willing to turn off the integrity
      requirement, it should respond with a 534 reply code.

      Otherwise, the server must reply with a 200 reply code to indicate
      that unprotected commands and replies may now be used on the
      command channel.


	 * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		processor.reset();
		processor.reply(REPLY_200_OK,"OK");
	}

}
