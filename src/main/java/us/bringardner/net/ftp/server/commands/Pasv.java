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
 * ~version~V000.01.35-V000.01.33-V000.01.19-V000.01.05-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.net.ftp.server.commands;

import java.io.IOException;

import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FtpCommand;
import us.bringardner.net.ftp.server.FtpRequestProcessor;
import us.bringardner.net.ftp.server.PassiveSocket;


/**
 * @author Tony Bringardner
 *
 */
public class Pasv extends BaseCommand implements FtpCommand {

    private static final long serialVersionUID = 1L;

	/**
     * 
     */
    public Pasv() {
        super(PASV);

    }

    /* 
     * RFC 959
     * PASSIVE (PASV)

            This command requests the server-PasvSocket to "listen" on a data
            port (which is not its default data port) and to wait for a
            connection rather than initiate one upon receipt of a
            transfer command.  The response to this command includes the
            host and port address this server is listening on.


     * 
     * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
     */
    public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {

        PassiveSocket pasvSocket = new PassiveSocket(processor);

        Thread t = new Thread(pasvSocket);
        t.setName("FTP_PasvSocket");
        t.start();
        int timeout = processor.getServer().getAcceptTimeout();
        
        long start = System.currentTimeMillis();
        while((System.currentTimeMillis()-start < timeout)
        		&& 
        		(!pasvSocket.isRunning() && 
        		!pasvSocket.isComplete())
        	) {
            // Wait for this guy to get up and running.
            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
            }
        }
        processor.logDebug("pasvSocket should be running now running="+pasvSocket.isRunning()+" coomplete="+pasvSocket.isComplete()+" error="+pasvSocket.isError()+" output="+pasvSocket.toString());
        processor.setPasvSocket(pasvSocket);
        processor.reply(REPLY_227_ENTERING_PASSIVE_MODE,"Entering Passive Mode ("+pasvSocket.toString()+")");

    }

}
