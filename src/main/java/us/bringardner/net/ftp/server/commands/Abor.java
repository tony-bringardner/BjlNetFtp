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
 * ~version~V000.01.37-V000.01.35-V000.01.09-V000.01.05-V000.00.01-V000.00.00-
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
public class Abor extends NoAuthReqBaseCommand {

	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public Abor() {
		super(ABOR);
	
	}

	/* 
	 * RFC 959  
	 *   ABORT (ABOR)

            This command tells the server to abort the previous FTP
            service command and any associated transfer of data.  The
            abort command may require "special action", as discussed in
            the Section on FTP Commands, to force recognition by the
            server.  No action is to be taken if the previous command
            has been completed (including data transfer).  The control
            connection is not to be closed by the server, but the data
            connection must be closed.

            There are two cases for the server upon receipt of this
            command: (1) the FTP service command was alREAD_PERMISSIONy completed,
            or (2) the FTP service command is still in progress.
            
            In the first case, the server closes the data connection
            (if it is open) and responds with a 226 reply, indicating
            that the abort command was successfully processed.

            In the second case, the server aborts the FTP service in
            progress and closes the data connection, returning a 426
            reply to indicate that the service request terminated
            abnormally.  The server then sends a 226 reply,
            indicating that the abort command was successfully
            processed.

	 */
	public void execute(FtpRequestProcessor processor, IRequestContext commandLine) throws IOException {
		//transferInProcess does everything
		processor.transferInProcess.abort();
	}

}
