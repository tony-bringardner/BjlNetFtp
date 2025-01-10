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
 * ~version~V000.01.49-V000.01.35-V000.01.19-V000.01.11-V000.01.05-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.net.ftp.server.commands.rfc2428;

import java.io.IOException;

import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FtpCommand;
import us.bringardner.net.ftp.server.FtpRequestProcessor;
import us.bringardner.net.ftp.server.PassiveSocket;
import us.bringardner.net.ftp.server.commands.BaseCommand;


/**
 * @author Tony Bringardner
 *
 */
public class Epsv extends BaseCommand implements FtpCommand {

    private static final long serialVersionUID = 1L;

	/**
     * 
     */
    public Epsv() {
        super(EPSV);

    }

    /* 
RFC 2428            FTP Extensions for IPv6 and NATs      September 1998

 The EPSV command requests that a server listen on a data port and
   wait for a connection.  The EPSV command takes an optional argument.
   The response to this command includes only the TCP port number of the
   listening connection.  The format of the response, however, is
   similar to the argument of the EPRT command.  This allows the same
   parsing routines to be used for both commands.  In addition, the
   format leaves a place holder for the network protocol and/or network
   address, which may be needed in the EPSV response in the future.  The
   response code for entering passive mode using an extended address
   MUST be 229.  The interpretation of this code, according to [PR85]
   is:

        2yz Positive Completion
        x2z Connections
        xy9 Extended Passive Mode Entered

   The text returned in response to the EPSV command MUST be:

        <text indicating server is entering extended passive mode> \
            (<d><d><d><tcp-port><d>)

   The portion of the string enclosed in parentheses MUST be the exact
   string needed by the EPRT command to open the data connection, as
   specified above.

   The first two fields contained in the parenthesis MUST be blank.  The
   third field MUST be the string representation of the TCP port number
   on which the server is listening for a data connection.  The network
   protocol used by the data connection will be the same network
   protocol used by the control connection.  In addition, the network
   address used to establish the data connection will be the same
   network address used for the control connection.  An example response
   string follows:

        Entering Extended Passive Mode (|||6446|)

   The standard negative error codes 500 and 501 are sufficient to
   handle all errors involving the EPSV command (e.g., syntax errors).

   When the EPSV command is issued with no argument, the server will
   choose the network protocol for the data connection based on the
   protocol used for the control connection.  However, in the case of
   proxy FTP, this protocol might not be appropriate for communication
   between the two servers.  Therefore, the client needs to be able to
   request a specific protocol.  If the server returns a protocol that
   is not supported by the host that will be connecting to the port, the





   client MUST issue an ABOR (abort) command to allow the server to
   close down the listening connection.  The client can then send an
   EPSV command requesting the use of a specific network protocol, as
   follows:

        EPSV<space><net-prt>

   If the requested protocol is supported by the server, it SHOULD use
   the protocol.  If not, the server MUST return the 522 error messages
   as outlined in section 2.

   Finally, the EPSV command can be used with the argument "ALL" to
   inform Network Address Translators that the EPRT command (as well as
   other data commands) will no longer be used.  An example of this
   command follows:

        EPSV<space>ALL

   Upon receipt of an EPSV ALL command, the server MUST reject all data
   connection setup commands other than EPSV (i.e., EPRT, PORT, PASV, et
   al.).  This use of the EPSV command is further explained in section
   4.

     * 
     * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
     */
    public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
    	processor.logDebug("Enter EPSV command  secure = "+processor.isSecure());
    	/*
    	 *  EPSV<space><net-prt>

   If the requested protocol is supported by the server, it SHOULD use
   the protocol.  If not, the server MUST return the 522 error messages
   as outlined in section 2.
    	 */
    	if(context.hasNext()) {
    		//String net_prt = context.getRemainingTokens();
    		//???
    	}
        PassiveSocket pasvSocket = new PassiveSocket(processor);

        Thread t = new Thread(pasvSocket);
        t.setName("FTP_PasvSocket");
        t.start();
        while(!pasvSocket.isRunning() && !pasvSocket.isComplete()) {
            // Wait for this guy to get up and running.
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
            }
        }
        processor.logDebug("pasvSocket should be running now running="+pasvSocket.isRunning()+" complete="+pasvSocket.isComplete()+" error="+pasvSocket.isError());
        processor.logDebug("pasvSocket toString="+pasvSocket.toString()+" EPSV=(|||"+pasvSocket.getPort()+"|)");
        processor.setPasvSocket(pasvSocket);
        /*
 		The text returned in response to the EPSV command MUST be:

        <text indicating server is entering extended passive mode> (<d><d><d><tcp-port><d>)
         */
        processor.reply(REPLY_227_ENTERING_PASSIVE_MODE,"Entering Extended Passive Mode (|||"+pasvSocket.getPort()+"|)");
        processor.logDebug("Exit EPSV command  secure = "+processor.isSecure());

    }

}
