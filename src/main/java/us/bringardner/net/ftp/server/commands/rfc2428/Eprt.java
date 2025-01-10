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
 * ~version~V000.01.49-V000.01.42-V000.01.41-V000.01.35-V000.01.33-V000.01.11-V000.01.05-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.net.ftp.server.commands.rfc2428;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import us.bringardner.core.SecureBaseObject;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FtpCommand;
import us.bringardner.net.ftp.server.FtpRequestProcessor;
import us.bringardner.net.ftp.server.commands.BaseCommand;


/**
 * @author Tony Bringardner
 *
 */
public class Eprt extends BaseCommand implements FtpCommand {

	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public Eprt() {
		super(EPRT);

	}

	/* 
	 * Received line=EPRT |2|::|64585|
	RFC 2428            FTP Extensions for IPv6 and NATs      September 1998


	The FTP commandsPORT and PASV are replaced with EPRT and EPSV, respectively.


	 The EPRT command allows for the specification of an extended address
   for the data connection.  The extended address MUST consist of the
   network protocol as well as the network and transport addresses.  The
   format of EPRT is:

           EPRT<space><d><net-prt><d><net-addr><d><tcp-port><d>

   The EPRT command keyword MUST be followed by a single space (ASCII
   32).  Following the space, a delimiter character (<d>) MUST be
   specified.  The delimiter character MUST be one of the ASCII
   characters in range 33-126 inclusive.  The character "|" (ASCII 124)
   is recommended unless it coincides with a character needed to encode
   the network address.

   The <net-prt> argument MUST be an address family number defined by
   IANA in the latest Assigned Numbers RFC (RFC 1700 [RP94] as of the
   writing of this document).  This number indicates the protocol to be
   used (and, implicitly, the address length).  This document will use
   two of address family numbers from [RP94] as examples, according to
   the following table:

        AF Number   Protocol
        ---------   --------
        1           Internet Protocol, Version 4 [Pos81a]
        2           Internet Protocol, Version 6 [DH96]

   The <net-addr> is a protocol specific string representation of the
   network address.  For the two address families specified above (AF
   Number 1 and 2), addresses MUST be in the following format:

        AF Number   Address Format      Example
        ---------   --------------      -------
        1           dotted decimal      132.235.1.2
        2           IPv6 string         1080::8:800:200C:417A
                    representations
                    defined in [HD96]






   The <tcp-port> argument must be the string representation of the
   number of the TCP port on which the host is listening for the data
   connection.

   The following are sample EPRT commands:

        EPRT |1|132.235.1.2|6275|

        EPRT |2|1080::8:800:200C:417A|5282|

   The first command specifies that the server should use IPv4 to open a
   data connection to the host "132.235.1.2" on TCP port 6275.  The
   second command specifies that the server should use the IPv6 network
   protocol and the network address "1080::8:800:200C:417A" to open a
   TCP data connection on port 5282.

   Upon receipt of a valid EPRT command, the server MUST return a code
   of 200 (Command OK).  The standard negative error code 500 and 501
   [PR85] are sufficient to handle most errors (e.g., syntax errors)
   involving the EPRT command.  However, an additional error code is
   needed.  The response code 522 indicates that the server does not
   support the requested network protocol.  The interpretation of this
   new error code is:

        5yz Negative Completion
        x2z Connections
        xy2 Extended Port Failure - unknown network protocol

   The text portion of the response MUST indicate which network
   protocols the server does support.  If the network protocol is
   unsupported, the format of the response string MUST be:

        <text stating that the network protocol is unsupported> \
            (prot1,prot2,...,protn)

   Both the numeric code specified above and the protocol information
   between the characters '(' and ')' are intended for the software
   automata receiving the response; the textual message between the
   numeric code and the '(' is intended for the human user and can be
   any arbitrary text, but MUST NOT include the characters '(' and ')'.
   In the above case, the text SHOULD indicate that the network protocol
   in the EPRT command is not supported by the server.  The list of
   protocols inside the parenthesis MUST be a comma separated list of
   address family numbers.  Two example response strings follow:

        Network protocol not supported, use (1)

        Network protocol not supported, use (1,2)
       EPRT |1|132.235.1.2|6275|
        EPRT |2|1080::8:800:200C:417A|5282|

	 * 
	 * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		processor.setPasvSocket(null);

		String commandLine = context.getCommandLine();
		processor.logDebug("Enter EPRT command '"+commandLine+"' secure = "+processor.isSecure());
		String [] args = commandLine.split(" ");
		if( args.length != 2 || args[1].isEmpty()) {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Not enough parameters");
			return;
		}
		
		try	{
			//EPRT<space> <d> <net-prt> <d> <net-addr> <d> <tcp-port> <d>
			char d = args[1].charAt(0);
			String data = args[1].substring(1);
			String parts[] = data.split("["+d+"]");
			if( parts.length != 3) {
				processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Not enough parts in "+data);
				return;
			}
		
			int port = Integer.parseInt(parts[2].trim());

			InetAddress addr = InetAddress.getByName(parts[1]);
			SocketFactory factory = processor.getSocketFactory();

			Socket command = processor.getConnection().getSocket();
			InetAddress mine = command.getLocalAddress();
			Socket socket = null;
			
			int timeout = processor.getServer().getAcceptTimeout();

			long start = System.currentTimeMillis();
			while((System.currentTimeMillis()-start < timeout) && socket == null ) {
				try {
					socket = createSocket(factory,addr,port,mine,100);
				}catch(IOException e){
					//  Give the client some time
					try {
						Thread.sleep(10);
					} catch (InterruptedException ex) {                    
					}                
				}
			}
			
			if( socket == null ) {
				throw new SocketTimeoutException("Timedout waiting for client");
			}
			processor.setDataSocket(socket);

			/*
			 * 		200 ok
			 *     	5yz Negative Completion
			 *		x2z Connections
			 * 		xy2 Extended Port Failure - unknown network protocol
			 */
			processor.reply(REPLY_200_OK,"port " + port + " of " + addr+" Local ="+socket.getLocalAddress()+":"+socket.getLocalPort());
		}catch(IOException e){            
			processor.reply(REPLY_425_CANT_OPEN_DATA_CON,"can't open data connection e="+e );
			// Just in case
			processor.setDataSocket(null);
		}

		processor.logDebug("Exit EPRT command '"+commandLine+"'");
	}

	private Socket createSocket(SocketFactory factory, InetAddress addr, int port, InetAddress mine, int timeout) throws IOException{
		if (factory instanceof SSLSocketFactory	) {
			SSLSocket ret = (SSLSocket) factory.createSocket();
			// Some clients don't support v1.3 
			String force = System.getProperty(SecureBaseObject.PROPERTY_FORCE_TLS_VERSION);
			if( force != null) {
				force = force.trim();
				if( !force.isEmpty()) {
					ret.setEnabledProtocols(new String[] {force});		
				}
			}
			
			ret.setUseClientMode(false);
			InetSocketAddress sa = new InetSocketAddress(addr,port);
			ret.connect(sa, timeout);
			return ret;
		} else {
			return factory.createSocket(addr, port);
		}
		
	}


}
