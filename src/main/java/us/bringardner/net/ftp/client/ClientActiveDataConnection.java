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
 * ~version~V000.01.33-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 13, 2006
 *
 */
package us.bringardner.net.ftp.client;

import java.io.IOException;

/**
 * Coordinate a passive data connection with the server by issuing a PASV command to get teh host and port from the server.
 * 
 * @author Tony Bringardner
 *
 */
public class ClientActiveDataConnection extends ClientDataTransferProcess {

    public ClientActiveDataConnection(FtpClient client) throws IOException {
        setClient(client);
        setPassive(false);
        
    	/* 
    	 * RFC 959
    	 * DATA PORT (PORT)

                The argument is a HOST-PORT specification for the data port
                to be used in data connection.  There are defaults for both
                the user and server data ports, and under normal
                circumstances this command and its reply are not needed.  If
                this command is used, the argument is the concatenation of a
                32-bit Internet host address and a 16-bit TCP port address.
                This address information is broken into 8-bit fields and the
                value of each field is transmitted as a decimal number (in
                character string representation).  The fields are separated
                by commas.  A port command would be:

                   PORT h1,h2,h3,h4,p1,p2

                where h1 is the high order 8 bits of the Internet host
                address.

    	 */
        
        ClientFtpResponse res = client.executeCommand(PORT);
        if( !res.isPositiveComplet()) {
            throw new IOException("Invalid response from "+PORT+" command = "+res._getResponseCode());
        }
        setHostAndPort(res.getResponseText());
        
    }
    
    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    public void run() {
        //  Force a connect;
        try {
            getSocket();
        } catch (Exception ex) {
            logError("Active connection error connecting to "+getHost()+":"+getPort(),ex);
       }
        
        stop();
    }
    
    

}
