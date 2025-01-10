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
public class Pbsz extends NoAuthReqBaseCommand {

	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public Pbsz() {
		super(PBSZ);
	
	}

	/*
 RFC 2228                FTP Security Extensions             October 1997

   PROTECTION BUFFER SIZE (PBSZ)

      The argument is a decimal integer representing the maximum size,
      in bytes, of the encoded data blocks to be sent or received during
      file transfer.  This number shall be no greater than can be
      represented in a 32-bit unsigned integer.

      This command allows the FTP client and server to negotiate a
      maximum protected buffer size for the connection.  There is no
      default size; the client must issue a PBSZ command before it can
      issue the first PROT command.

      The PBSZ command must be preceded by a successful security data
      exchange.

      If the server cannot parse the argument, or if it will not fit in
      32 bits, it should respond with a 501 reply code.

      If the server has not completed a security data exchange with the
      client, it should respond with a 503 reply code.

      Otherwise, the server must reply with a 200 reply code.  If the
      size provided by the client is too large for the server, it must
      use a string of the form "PBSZ=number" in the text part of the
      reply to indicate a smaller buffer size.  The client and the
      server must use the smaller of the two buffer sizes if both buffer
      sizes are specified.

	
		PBSZ
            200
            503
            500, 501, 421, 530


	 * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		String commandLine = context.getCommandLine();
		String [] args = commandLine.split(" ");
		if( args.length != 2) {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Wrong number of args");
		} else if( !processor.isChannelSecure()) {
			processor.reply(REPLY_503_BAD_SEQ_OF_COMMANDS,"Channel is not secure");
		} else 	{
			try {
				int val = Integer.parseInt(args[1]);
				processor.setPbsz(val);
				processor.reply(REPLY_200_OK,"OK");
			} catch(Exception ex) {
				processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Error parsing PBSZ ("+ex+")");
			}
		}
		
	}

}
