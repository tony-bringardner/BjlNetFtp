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
 * ~version~V000.01.37-V000.01.35-V000.01.05-V000.00.03-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.net.ftp.server.commands;


import java.io.IOException;

import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FeatCommand;
import us.bringardner.net.ftp.server.FtpRequestProcessor;


/**
 * @author Tony Bringardner
 *
 */
public class Rest extends BaseCommand implements FeatCommand {

	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public Rest() {
		super(REST);
	
	}

	/* 
	 * RFC 959
	 *  RESTART (REST)

            The argument field represents the server marker at which
            file transfer is to be restarted.  This command does not
            cause file transfer but skips over the file to the specified
            data checkpoint.  This command shall be immediately followed
            by the appropriate FTP service command which shall cause
            file transfer to resume.
 
             REST
                  500, 501, 502, 421, 530
                  350 (Requested file action pending further information)

REST was enhanced in Internet Draft        draft-ietf-ftpext-mlst-16.txt       September 2002
http://www.ietf.org/internet-drafts/draft-ietf-ftpext-mlst-16.txt

5.3. Syntax

   The syntax for the REST command when the current transfer mode is
   STREAM is:

        rest          = "Rest" SP 1*DIGIT CRLF

   The numeric value gives the number of octets of the immediately
   following transfer to not actually send, effectively causing the
   transmission to be restarted at a later point.  A value of zero
   effectively disables restart, causing the entire file to be
   transmitted.  The server-PI will respond to the REST command with a
   350 reply, indicating that the REST parameter has been saved, and
   that another command, which should be either RETR or STOR, should
   then follow to complete the restart.

        rest-response = "350" SP *TCHAR CRLF /
                        error-response

   Server-FTP processes may permit transfer commands other than RETR and
   STOR, such as APPE and STOU, to complete a restart, however, this is
   not recommended.  STOU (store unique) is undefined in this usage, as
   storing the remainder of a file into a unique file name is rarely
   going to be useful.  If APPE (append) is permitted, it MUST act
   identically to STOR when a restart marker has been set.  That is, in
   both cases, octets from the data connection are placed into the file
   at the location indicated by the restart marker value.

   The REST command is intended to complete a failed transfer.  Use with
   RETR is comparatively well defined in all cases, as the client bears
   the responsibility of merging the retrieved data with the partially
   retrieved file.  If it chooses to use the data obtained other than to
   complete an earlier transfer, or if it chooses to re-retrieve data
   that had been retrieved before, that is its choice.  With STOR,
   however, the server must insert the data into the file named.  The
   results are undefined if a client uses REST to do other than restart
   to complete a transfer of a file which had previously failed to
   completely transfer.  In particular, if the restart marker set with a
   REST command is not at the end of the data currently stored at the
   server, as reported by the server, or if insufficient data are
   provided in a STOR that follows a REST to extend the destination file
   to at least its previous size, then the effects are undefined.


   The REST command must be the last command issued before the data
   transfer command which is to cause a restarted rather than complete
   file transfer.  The effect of issuing a REST command at any other
   time is undefined.  The server-PI may react to a badly positioned
   REST command by issuing an error response to the following command,
   not being a restartable data transfer command, or it may save the
   restart value and apply it to the next data transfer command, or it
   may silently ignore the inappropriate restart attempt.  Because of
   this, a user-PI that has issued a REST command, but which has not
   successfully transmitted the following data transfer command for any
   reason, should send another REST command before the next data
   transfer command.  If that transfer is not to be restarted, then
   "REST 0" should be issued.

   An error-response will follow a REST command only when the server
   does not implement the command, or the restart marker value is
   syntactically invalid for the current transfer mode.  That is, in
   STREAM mode, if something other than one or more digits appears in
   the parameter to the REST command.  Any other errors, including such
   problems as restart marker out of range, should be reported when the
   following transfer command is issued.  Such errors will cause that
   transfer request to be rejected with an error indicating the invalid
   restart attempt.



	 * 
	 * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		String commandLine = context.getCommandLine();
		int idx = commandLine.indexOf(' ');
		if( idx > 0 ){
			commandLine = commandLine.substring(idx).trim();
		} else {
			processor.reply(REPLY_450_FILE_ACTION_FAILED, "\"" + processor.getCurrentDir()+"\"File Action Pending");
			return;
		}

		
		try {
			Long marker = (Long.parseLong(commandLine));
			processor.setTempValue(REST,marker);
			processor.reply(REPLY_350_FILE_ACTION_PENDING,"Restarting at "+marker+". Send STORE or RETRIEVE"); 
					
		} catch(Exception ex){	
			processor.reply(REPLY_500_SYNTAX_ERROR, "Marker "+commandLine+" could not be converted to a number ex="+ex);
		}
		

	}

	/* (non-Javadoc)
	 * @see us.bringardner.net.ftp.server.FeatCommand#getFeatResponse()
	 */
	public String getFeatResponse(FtpRequestProcessor processor) {

		return REST+" STREAM";
	}

	@Override
	public IPermission getPermission() {
		return WRITE_PERMISSION;
	}

}
