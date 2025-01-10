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
 * ~version~V000.01.50-V000.01.03-V000.01.02-V000.00.01-V000.00.00-
 */
/*
 * Created on Nov 24, 2006
 * Holds the response of an FTP Command
 */
package us.bringardner.net.ftp.client;

import java.io.IOException;

import us.bringardner.net.ftp.FTP;

public class ClientFtpResponse implements FTP {
    private int responseCode = REPLY_421_SERVICE_NOT_AVAILIBLE;
    private String responseText = "No Response";

     /**
     * Read a multi line FTP response from the server.
     * See RFC 959, section 4.2
     * 
     * @param ftpClient
     * @return
     * @throws IOException
     */
    private String readMultiLineResponse(FtpClient ftpClient) throws IOException {
        StringBuffer ret = new StringBuffer();
        //  Response terminator
        String code = ""+responseCode;
        String term = code+" ";
        
        boolean done = false;
        while(!done) {
            String line = ftpClient.readLine();
            ret.append("\n");
            if( line == null ) {
            	done = true;
            } else if(line.startsWith(term)) {
                done = true;
                ret.append(line.substring(4).trim());
            } else {
                if(line.startsWith(code)) {
                    ret.append(line.substring(3).trim());
                } else {
                    ret.append(line);
                }
            }
        }
        
        return ret.toString();
    }
    
    /**
     * Read an FTP response from the server.
     * See RFC 959, section 4.2
     * 
     * @param ftpClient
     * @throws IOException
     */
    public void readResponse(FtpClient ftpClient) throws IOException {
        String line = ftpClient.readLine(); 
        int idx = 3;
        
        if( line != null && line.length() >= idx) {
            String tmp = line.substring(0,idx);
            responseCode = Integer.parseInt(tmp);
            
           
            line = line.substring(idx);
            if(line.startsWith("-") ) {
                tmp = readMultiLineResponse(ftpClient);
                responseText=line.substring(idx-1)+tmp;
            } else {
                responseText = line;
            }
        }
    }
    
    /**
     * @return the response code
     */
    public int _getResponseCode() {
        return responseCode;
    }
    
    /**
     * @param responseCode
     */
    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }
    
    /**
     * @return text read from the response.  This may contain multiple lines seperated with a '\n'
     */
    public String getResponseText() {
        return responseText;
    }
    
    /**
     * @param responseText read from the server.
     */
    public void setResponseText(String responseText) {
        this.responseText = responseText;
    }
    
    public String toString() {
        return ""+_getResponseCode()+" "+getResponseText();
    }

    /**
     *  RFC 959 (4.2)
     * 1yz   Positive Preliminary reply
     *
     *          The requested action is being initiated; expect another
     *          reply before proceeding with a new command.  (The
     *          user-process sending another command before the
     *          completion reply would be in violation of protocol; but
     *          server-FTP processes should queue any commands that
     *          arrive while a preceding command is in progress.)  This
     *          type of reply can be used to indicate that the command
     *          was accepted and the user-process may now pay attention
     *          to the data connections, for implementations where
     *          simultaneous monitoring is difficult.  The server-FTP
     *          process may send at most, one 1yz reply per command.
     *
     *@return true if the response code is between 100 and 199
     */
    public boolean isPositivePreliminay() {
        
        return responseCode >=100 && responseCode <= 199;
    }

    
    /**
     * RFC 959 (4.2)
     * 2yz   Positive Completion reply
     *
     *         The requested action has been successfully completed.  A
     *         new request may be initiated.
     *
     * @return true if the response code is between 200 and 299
     */
    public boolean isPositiveComplet() {
        
        return responseCode >=200 && responseCode <= 299;
    }
    
    /**
     *  RFC 959 (4.2)
     * 3yz   Positive Intermediate reply

               The command has been accepted, but the requested action
               is being held in abeyance, pending receipt of further
               information.  The user should send another command
               specifying this information.  This reply is used in
               command sequence groups.

     * @return true if the response code is between 300 and 399
     */
    public boolean isPositiveIntermediate() {
        
        return responseCode >=300 && responseCode <= 399;
    }
    

    /**
    *  RFC 959 (4.2)
    *  
    *  4yz   Transient Negative Completion reply
    *
    *          The command was not accepted and the requested action did
    *          not take place, but the error condition is temporary and
    *          the action may be requested again.  The user should
    *          return to the beginning of the command sequence, if any.
    *          It is difficult to assign a meaning to "transient",
    *          particularly when two distinct sites (Server- and
    *          User-processes) have to agree on the interpretation.
    *          Each reply in the 4yz category might have a slightly
    *          different time value, but the intent is that the
    *          user-process is encouraged to try again.  A rule of thumb
    *          in determining if a reply fits into the 4yz or the 5yz
    *          (Permanent Negative) category is that replies are 4yz if
    *          the commands can be repeated without any change in
    *          command form or in properties of the User or Server
    *          (e.g., the command is spelled the same with the same
    *          arguments used; the user does not change his file access
    *          or user name; the server does not put up a new
    *          implementation.)
    *
    * @return true if the response code is between 400 and 499
    */
   public boolean isNegativeTransient() {
       
       return responseCode >=400 && responseCode <= 499;
   }
   
   /**
    *  RFC 959 (4.2)
    *  
    *  5yz   Permanent Negative Completion reply
    *
    *           The command was not accepted and the requested action did
    *           not take place.  The User-process is discouraged from
    *           repeating the exact request (in the same sequence).  Even
    *           some "permanent" error conditions can be corrected, so
    *           the human user may want to direct his User-process to
    *           reinitiate the command sequence by direct action at some
    *           point in the future (e.g., after the spelling has been
    *           changed, or the user has altered his directory status.)
    *
    * @return true if the response code is between 500 and 599
    */
   public boolean isNegativePerminent() {   
       return responseCode >=500 && responseCode <= 599;
   }
   
   /**
    * Any negative reply, permanent or transient.  Many clients will
    * not distinguish between them (IMHO).
    * 
    * @return true if the response code is between 400 and 599
    * @see #isNegativePerminent()
    * @see #isNegativeTransient()
    */
   public boolean isNegative() {   
       return responseCode >=400 && responseCode <= 599;
   }

}
