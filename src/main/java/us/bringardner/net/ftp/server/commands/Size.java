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
 * ~version~V000.01.37-V000.01.35-V000.01.09-V000.01.08-V000.01.07-V000.01.05-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 22, 2004
 *
 */
package us.bringardner.net.ftp.server.commands;

import java.io.IOException;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FeatCommand;
import us.bringardner.net.ftp.server.FtpRequestProcessor;

/**
 *  The FTP command, SIZE OF FILE
 *  
 * @author Tony Bringardner
 *
 */
public class Size extends BaseCommand implements FeatCommand {

	
	private static final long serialVersionUID = 1L;

	public Size() {
		super(SIZE);
	}

	/*
	 * FTP Extensions
	 * http://www.ietf.org/internet-drafts/draft-ietf-ftpext-mlst-16.txt
	 * 
	 * 4. File SIZE

   The FTP command, SIZE OF FILE (SIZE), is used to obtain the transfer
   size of a file from the server-FTP process.  That is, the exact
   number of octets (8 bit bytes) which would be transmitted over the
   data connection should that file be transmitted.  This value will
   change depending on the current STRUcture, MODE and TYPE of the data
   connection, or a data connection which would be created were one
   created now.  Thus, the result of the SIZE command is dependent on
   the currently established STRU, MODE and TYPE parameters.

   The SIZE command returns how many octets would be transferred if the
   file were to be transferred using the current transfer structure,
   mode and type.  This command is normally used in conjunction with the
   RESTART (REST) command when STORing a file to a remote server in
   STREAM mode, to determine the restart point.  The server-PI might
   need to READ_PERMISSION the partially transferred file, do any appropriate
   conversion, and count the number of octets that would be generated
   when sending the file in order to correctly respond to this command.
   Estimates of the file transfer size MUST NOT be returned, only
   precise information is acceptable.

4.1. Syntax

   The syntax of the SIZE command is:

        size          = "Size" SP pathname CRLF

   The server-PI will respond to the SIZE command with a 213 reply
   giving the transfer size of the file whose pathname was supplied, or
   an error response if the file does not exist, the size is
   unavailable, or some other error has occurred.  The value returned is
   in a format suitable for use with the RESTART (REST) command for mode
   STREAM, provided the transfer mode and type are not altered.

 

	 * @see us.bringardner.net.ftp.server.commands.BaseCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context)
		throws IOException {
		
		/*
		 * Only supported for image type.  
		 * 
		 * Where the command is correctly parsed, but the size is not available,
   perhaps because the pathname identifies no existing entity, or
   because the entity named cannot be transferred in the current MODE
   and TYPE (or at all), then a 550 reply should be sent.  Where the
   command cannot be correctly parsed, a 500 or 501 reply should be
   sent, as specified in [3].  The presence of the 550 error response to
   a SIZE command MUST NOT be taken by the client as an indication that
   the file can not be transferred in the current MODE and TYPE.  A
   server may generate this error for other reasons -- for instance if
   the processing overhead is considered too great.  Various 4xy replies
   are also possible in appropriate circumstances.

		 */
		/*
		if( !processor.isImageMode()){
			processor.reply(REPLY_550_ACTION_NOT_TAKEN,getName()+" only supported for image mode");
			return;
		}
		*/
		
		String commandLine = context.getCommandLine();
		int idx = commandLine.indexOf(' ');
		if( idx > 0 ){
			commandLine = commandLine.substring(idx);
		} else {
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM,"Not enough parameters");
			return;
		}
			
		FileSource target = processor.createNewFile(commandLine);
		
		if(target.isFile()){
			processor.reply(REPLY_213_FILE_STATUS,""+target.length());
		} else {
			processor.reply(REPLY_550_ACTION_NOT_TAKEN," size not availible for "+commandLine);
		}
		


	}

	/* (non-Javadoc)
	 * @see us.bringardner.net.ftp.server.FeatCommand#getFeatResponse()
	 */
	public String getFeatResponse(FtpRequestProcessor processor) {
		
		return SIZE;
	}

}
