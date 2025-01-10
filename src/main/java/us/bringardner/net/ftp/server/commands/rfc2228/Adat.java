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
public class Adat extends NoAuthReqBaseCommand {

	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public Adat() {
		super(ADAT);
	
	}

	/*
 RFC 2228                FTP Security Extensions             October 1997

   AUTHENTICATION/SECURITY DATA (ADAT)

      The argument field is a Telnet string representing base 64 encoded
      security data (see Section 9, "Base 64 Encoding").  If a reply
      code indicating success is returned, the server may also use a
      string of the form "ADAT=base64data" as the text part of the reply
      if it wishes to convey security data back to the client.

      The data in both cases is specific to the security mechanism
      specified by the previous AUTH command.  The ADAT command, and the
      associated replies, allow the client and server to conduct an
      arbitrary security protocol.  The security data exchange must
      include enough information for both peers to be aware of which
      optional features are available.  For example, if the client does
      not support data encryption, the server must be made aware of
      this, so it will know not to send encrypted command channel
      replies.  It is strongly recommended that the security mechanism
      provide sequencing on the command channel, to insure that commands
      are not deleted, reordered, or replayed.

      The ADAT command must be preceded by a successful AUTH command,
      and cannot be issued once a security data exchange completes
      (successfully or unsuccessfully), unless it is preceded by an AUTH
      command to reset the security state.

      If the server has not yet received an AUTH command, or if a prior
      security data exchange completed, but the security state has not
      been reset with an AUTH command, it should respond with reply code
      503.

      If the server cannot base 64 decode the argument, it should
      respond with reply code 501.

      If the server rejects the security data (if a checksum fails, for
      instance), it should respond with reply code 535.

      If the server accepts the security data, and requires additional
      data, it should respond with reply code 335.

      If the server accepts the security data, but does not require any
      additional data (i.e., the security data exchange has completed
      successfully), it must respond with reply code 235.

      If the server is responding with a 235 or 335 reply code, then it
      may include security data in the text part of the reply as
      specified above.

      If the ADAT command returns an error, the security data exchange
      will fail, and the client must reset its internal security state.
      If the client becomes unsynchronized with the server (for example,
      the server sends a 234 reply code to an AUTH command, but the
      client has more data to transmit), then the client must reset the
      server's security state.

	ADAT
            235
            335
            503, 501, 535
            500, 501, 421


	 * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		//String [] args = commandLine.split(" ");
		processor.reply(REPLY_502_NOT_IMPLEMENTED,getName()+" is not implemented.");

	}

}
