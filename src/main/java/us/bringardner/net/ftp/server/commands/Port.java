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
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FtpCommand;
import us.bringardner.net.ftp.server.FtpRequestProcessor;


/**
 * @author Tony Bringardner
 *
 */
public class Port extends BaseCommand implements FtpCommand {

	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public Port() {
		super(PORT);
	
	}

	/* 
	 * RFC 959
	 * DATA PORT (PORT)

            The argument is a HOST-PORT specification for the data port
            to be used in data connection.  There are defaults for both
            the user and server data ports, and under normal
            circumstances this command and its reply are not needed.  If
            this command is used, the argument is the concatenation of a
            32-bit internet host address and a 16-bit TCP port address.
            This address information is broken into 8-bit fields and the
            value of each field is transmitted as a decimal number (in
            character string representation).  The fields are separated
            by commas.  A port command would be:

               PORT h1,h2,h3,h4,p1,p2

            where h1 is the high order 8 bits of the internet host
            address.


	 * 
	 * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		if( !context.hasNext()) {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Not enough parameters");
			return;
		}
		String commandLine = context.getNextToken();
        processor.logDebug("Enter port command '"+commandLine+"'");
				
		String parts [] = commandLine.split("[,]");
		if( parts.length != 6) {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Argument does not have 6 parts ="+parts.length);
			return;
		}
		
		
		//  This will set ACTIVE mode
		processor.setPasvSocket(null);
		String ipaddrStr=String.format("%s.%s.%s.%s", parts[0],parts[1],parts[2],parts[3]);
		InetAddress addr = InetAddress.getByName(ipaddrStr);
		
		
		int port1 = Integer.parseInt(parts[4]);
		int port2 = Integer.parseInt(parts[5]);
		
		int portnum1 = port1*256;
		int portnum = portnum1+port2;
		processor.logDebug(String.format("port calulation %d*256=%d +%d = %d", port1,portnum1,port2, portnum));
		
		try	{
			
			Socket command = processor.getConnection().getSocket();
			InetAddress mine = command.getLocalAddress();
			
			Socket socket = null;
            int err = 0;
            long start = System.currentTimeMillis();
            int timeout = processor.getServer().getAcceptTimeout();
            
            while((System.currentTimeMillis()-start < timeout) && socket == null ) {
                try {
                    socket = processor.createSocket(addr,portnum,mine,10);
                }catch(IOException e){
                    if(++err<4){
                        //  Give the client some time
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException ex) {
                        }
                    } else {
                        // Accept the fact that we are not going to connect.
                        throw e;
                    }
                }                
            }
            if( socket == null ) {
            	throw new SocketTimeoutException("Time out waiting for pasive socket");
            }
			processor.setDataSocket(socket);
			
			processor.reply(REPLY_200_OK,"port " + portnum + " of " + addr+" Local ="+socket.getLocalAddress()+":"+socket.getLocalPort());
		}catch(IOException e){            
			processor.reply(REPLY_425_CANT_OPEN_DATA_CON,"can't open data connection e="+e );
			// Just in case
			processor.setDataSocket(null);
		}
		
        processor.logDebug("Exit port command '"+commandLine+"'");
	}
    

}
