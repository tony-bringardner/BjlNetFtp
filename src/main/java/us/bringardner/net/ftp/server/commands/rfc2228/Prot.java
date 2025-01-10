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
public class Prot extends NoAuthReqBaseCommand {

	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public Prot() {
		super(PROT);
	
	}

	/*
 RFC 2228                FTP Security Extensions             October 1997


 	 DATA CHANNEL PROTECTION LEVEL (PROT)

      The argument is a single Telnet character code specifying the data
      channel protection level.

      This command indicates to the server what type of data channel
      protection the client and server will be using.  The following
      codes are assigned:

         C - Clear
         S - Safe
         E - Confidential
         P - Private

      The default protection level if no other level is specified is
      Clear.  The Clear protection level indicates that the data channel
      will carry the raw data of the file transfer, with no security
      applied.  The Safe protection level indicates that the data will
      be integrity protected.  The Confidential protection level
      indicates that the data will be confidentiality protected.  The
      Private protection level indicates that the data will be integrity
      and confidentiality protected.

      It is reasonable for a security mechanism not to provide all data
      channel protection levels.  It is also reasonable for a mechanism
      to provide more protection at a level than is required (for
      instance, a mechanism might provide Confidential protection, but
      include integrity-protection in that encoding, due to API or other
      considerations).

      The PROT command must be preceded by a successful protection
      buffer size negotiation.

      If the server does not understand the specified protection level,
      it should respond with reply code 504.

      If the current security mechanism does not support the specified
      protection level, the server should respond with reply code 536.

      If the server has not completed a protection buffer size
      negotiation with the client, it should respond with a 503 reply
      code.

      The PROT command will be rejected and the server should reply 503
      if no previous PBSZ command was issued.

      If the server is not willing to accept the specified protection
      level, it should respond with reply code 534.

      If the server is not able to accept the specified protection
      level, such as if a required resource is unavailable, it should
      respond with reply code 431.

      Otherwise, the server must reply with a 200 reply code to indicate
      that the specified protection level is accepted.

	 PROT
            200
            504, 536, 503, 534, 431
            500, 501, 421, 530


	 * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		String commandLine = context.getCommandLine();
		String [] args = commandLine.split(" ");
		if( args.length != 2) {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Wrong number of args");
		} else if( processor.getPbsz() < 0 ) {
			processor.reply(REPLY_503_BAD_SEQ_OF_COMMANDS,"PBSZ has not been set.");
		} else 	{
			if( args[1].equals(DATA_CHANNEL_PROTECTION_LEVEL_CLEAR)
					|| args[1].equals(DATA_CHANNEL_PROTECTION_LEVEL_CONFIDENTIAL)
					|| args[1].equals(DATA_CHANNEL_PROTECTION_LEVEL_PRIVATE)
					|| args[1].equals(DATA_CHANNEL_PROTECTION_LEVEL_SAFE)
					) {
				processor.setProtLevel(args[1]);
				processor.reply(REPLY_200_OK,"OK");
			} else {
				processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Invalid Level ("+args[1]+")");
			}
		}
		
	}

}
