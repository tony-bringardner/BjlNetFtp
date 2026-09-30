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
         * Listen BEFORE sending PORT: servers may connect as soon as they receive it.
         * (This used to send a bare "PORT" with no address, which servers reject with 501.)
         */
        java.net.InetAddress local = client.getControlLocalAddress();
        java.net.ServerSocket listener = new java.net.ServerSocket();
        try {
            int bufSz = client.getTransferBufferSize();
            if( bufSz > 0 ) {
                listener.setReceiveBufferSize(bufSz);
            }
            listener.bind(new java.net.InetSocketAddress(local, 0), 1);
            setListener(listener);
            String host = local.getHostAddress();
            int pct = host.indexOf('%');
            if( pct > 0 ) {
                host = host.substring(0, pct); // drop IPv6 scope id
            }
            setHost(host);
            setPort(listener.getLocalPort());

            ClientFtpResponse res;
            if( local instanceof java.net.Inet4Address ) {
                res = client.executeCommand(PORT, getFormatedHostAndPort());
            } else {
                // RFC 2428 for IPv6
                res = client.executeCommand(EPRT, "|2|"+host+"|"+getPort()+"|");
            }
            if( !res.isPositiveComplet()) {
                throw new IOException("Invalid response from "+PORT+"/"+EPRT+" command = "+res);
            }
        } catch (IOException e) {
            close();
            throw e;
        }
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
