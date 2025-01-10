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
 * ~version~V000.01.37-V000.01.35-V000.01.08-V000.01.07-V000.01.05-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.net.ftp.server.commands;

import java.io.IOException;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FeatCommand;
import us.bringardner.net.ftp.server.FtpRequestProcessor;

/**
 * @author Tony Bringardner
 *
 */
public class Mdtm  extends BaseCommand  implements FeatCommand {


	
	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public Mdtm() {
		super(MDTM);
	
	}

	/* 
	 * Internet Draft        draft-ietf-ftpext-mlst-16.txt       September 2002
	 * http://www.ietf.org/internet-drafts/draft-ietf-ftpext-mlst-16.txt
	 *  File Modification Time (MDTM)

   The FTP command, MODIFICATION TIME (MDTM), can be used to determine
   when a file in the server NVFS was last modified.  This command has
   existed in many FTP servers for many years, as an adjunct to the REST
   command for STREAM mode, thus is widely available.  However, where
   supported, the "modify" fact which can be provided in the result from
   the new MLST command is recommended as a superior alternative.

   When attempting to restart a RETRieve, if the User-FTP makes use of
   the MDTM command, or "modify" fact, it can check and see if the
   modification time of the source file is more recent than the
   modification time of the partially transferred file.  If it is, then
   most likely the source file has changed and it would be unsafe to
   restart the previously incomplete file transfer.

   Because the User and server FTPs' clocks are not necessarily
   synchronized, User FTPs intending to use this method should usually
   obtain the modification time of the file from the server before the
   initial RETRieval, and compare that with the modification time before
   a RESTart.  If they differ, the files may have changed, and RESTart
   would be inadvisable.  Where this is not possible, the User FTP
   should make sure to allow for possible clock skew when comparing
   times.

   When attempting to restart a STORe, the User FTP can use the MDTM
   command to discover the modification time of the partially
   transferred file.  If it is older than the modification time of the
   file that is about to be STORed, then most likely the source file has
   changed and it would be unsafe to restart the file transfer.

   Note that using MLST (described below) where available, can provide
   this information, and much more, thus giving an even better
   indication that a file has changed, and that restarting a transfer
   would not give valid results.

   Note that this is applicable to any RESTart attempt, regardless of
   the mode of the file transfer.

3.1. Syntax

   The syntax for the MDTM command is:

        mdtm          = "MdTm" SP pathname CRLF

   As with all FTP commands, the "MDTM" command label is interpreted in
   a case insensitive manner.

   The "pathname" specifies an object in the NVFS which may be the
   object of a RETR command.  Attempts to query the modification time of
   files that exist but are unable to be retrieved may generate an
   error-response, or can result in a positive response carrying a time-
   val with an unspecified value, the choice being made by the server-
   PI.

   The server-PI will respond to the MDTM command with a 213 reply
   giving the last modification time of the file whose pathname was
   supplied, or a 550 reply if the file does not exist, the modification
   time is unavailable, or some other error has occurred.

        mdtm-response = "213" SP time-val CRLF /
                        error-response

   Note that when the 213 response is issued, that is, when there is no
   error, the format MUST be exactly as specified.  Multi-line responses
   are not permitted.

3.2. Error responses

   Where the command is correctly parsed, but the modification time is
   not available, either because the pathname identifies no existing
   entity, or because the information is not available for the entity
   named, then a 550 reply should be sent.  Where the command cannot be
   correctly parsed, a 500 or 501 reply should be sent, as specified in
   [3].  Various 4xy replies are also possible in appropriate
   circumstances.




	 * 
	 * 
	 * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		String commandLine = context.getCommandLine();
		
		FileSource target = null;
		int idx = commandLine.indexOf(' ');
	
		if( idx <=0 ){
			processor.reply(REPLY_501_SYNTAXT_ERROR_IN_PARAM," path required.");
			return;
		}
		
		commandLine = commandLine.substring(idx);
		target = processor.createNewFile(commandLine);
			
		
		if( target == null || !target.exists() || target.isDirectory()){
			processor.reply(REPLY_450_FILE_ACTION_FAILED," Invalid or non existant name");
			return;
		} 
				
		processor.reply(REPLY_213_FILE_STATUS,Mlst.formatTime(target.lastModified()));
	}

	/* 
	 * @see us.bringardner.net.ftp.server.FeatCommand#getFeatResponse(us.bringardner.net.ftp.server.FtpRequestProcessor)
	 */
	public String getFeatResponse(FtpRequestProcessor processor) {

		return MDTM;
	}

	@Override
	public IPermission getPermission() {
		return READ_PERMISSION;
	}
}
