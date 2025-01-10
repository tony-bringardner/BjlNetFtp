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
import java.net.SocketException;

import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FtpRequestProcessor;

/**
 * @author Tony Bringardner
 *
 */
public class Quit extends NoAuthReqBaseCommand {

	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public Quit() {
		super(QUIT);
	
	}

	/* 
	 * RFC 959
	 * LOGOUT (QUIT)

            This command terminates a USER and if file transfer is not
            in progress, the server closes the control connection.  If
            file transfer is in progress, the connection will remain
            open for result response and the server will then close it.
            If the user-process is transferring files for several USERs
            but does not wish to close and then reopen connections for
            each, then the REIN command should be used instead of QUIT.

            An unexpected close on the control connection will cause the
            server to take the effective action of an abort (ABOR) and a
            logout (QUIT).


	 * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
        try {
            processor.reply(REPLY_OK,"Closing");
        } catch(SocketException ex) {
            //  Must likely because the client closed it's connection.  Ignore.
        }
        
		processor.stop();
	}

}
