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
 * ~version~V000.01.54-V000.01.47-V000.01.46-V000.01.44-V000.01.41-V000.01.37-V000.01.35-V000.01.34-V000.01.07-V000.01.05-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.net.ftp.server.commands;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FeatCommand;
import us.bringardner.net.ftp.server.FtpRequestProcessor;

/**
 * @author Tony Bringardner
 *
 */
public class Mlsd  extends BaseCommand  implements FeatCommand {


	private static final long serialVersionUID = 1L;


	/**
	 * 
	 */
	public Mlsd() {
		super(MLSD);
	}

	/* 
	 * Internet Draft        draft-ietf-ftpext-mlst-16.txt       September 2002 
	 *http://www.ietf.org/internet-drafts/draft-ietf-ftpext-mlst-16.txt
	 * 
	 *
	 *
   If no argument is given then MLSD must return a listing of the
   contents of the current working directory, and MLST must return a
   listing giving information about the current working directory
   itself.  For these purposes, the contents of a directory are whatever
   file or directory names (not pathnames) the server-PI will allow to
   be referenced when the current working directory is the directory
   named, and which the server-PI desires to reveal to the user-PI.
   Note that omitting the argument is the only defined way to obtain a
   listing of the current directory, unless a pathname that represents
   the directory happens to be known.  In particular, there is no
   defined shorthand name for the current directory.  This does not
   prohibit any particular server-PI implementing such a shorthand.

   No title, header, or summary, lines, or any other formatting, other
   than as is specified below, is ever returned in the output of an MLST
   or MLSD command.

   If the Client-FTP sends an invalid argument, the Server-FTP MUST
   reply with an error code of 501.

   The syntax for the MLSx command is:

        mlst             = "MLst" [ SP pathname ] CRLF
        mlsd             = "MLsD" [ SP pathname ] CRLF

7.2. Format of MLSx Response

   The format of a response to an MLSx command is as follows:

        mlst-response    = control-response / error-response
        mlsd-response    = ( initial-response final-response ) /
                           error-response

        control-response = "250-" [ response-message ] CRLF
                           1*( SP entry CRLF )
                           "250" [ SP response-message ] CRLF

        initial-response = "150" [ SP response-message ] CRLF
        final-response   = "226" SP response-message CRLF

        response-message = *TCHAR

        data-response    = *( entry CRLF )

        entry            = [ facts ] SP pathname
        facts            = 1*( fact ";" )
        fact             = factname "=" value
        factname         = "Size" / "Modify" / "Create" /
                           "Type" / "Unique" / "Perm" /
                           "Lang" / "Media-Type" / "CharSet" /
                           os-depend-fact / local-fact
        os-depend-fact   = <IANA assigned OS name> "." token
        local-fact       = "X." token
        value            = *SCHAR

   Upon receipt of a MLSx command, the server will verify the parameter,
   and if invalid return an error-response.  For this purpose, the
   parameter should be considered to be invalid if the client issuing
   the command does not have permission to perform the requested
   operation.


   If the command was an MLSD command, the server will open a data
   connection as indicated in section 3.2 of RFC959 [3].  If that fails,
   the server will return an error-response.  If all is OK, the server
   will return the initial-response, send the appropriate data-response
   over the new data connection, close that connection, and then send
   the final-response over the control connection.  The grammar above
   defines the format for the data-response, which defines the format of
   the data returned over the data connection established.

   The data connection opened for a MLSD response shall be a connection
   as if the "TYPE L 8", "MODE S", and "STRU F" commands had been given,
   whatever FTP transfer type, mode and structure had actually been set,
   and without causing those settings to be altered for future commands.
   That is, this transfer type shall be set for the duration of the data
   connection established for this command only.  While the content of
   the data sent can be viewed as a series of lines, implementations
   should note that there is no maximum line length defined.
   Implementations should be prepared to deal with arbitrarily long
   lines.

   The facts part of the specification would contain a series of "file
   facts" about the file or directory named on the same line.  Typical
   information to be presented would include file size, last
   modification time, creation time, a unique identifier, and a
   file/directory flag.

   The complete format for a successful reply to the MLSD command would
   be:

        facts SP pathname CRLF
        facts SP pathname CRLF
        facts SP pathname CRLF
        ...

   Note that the format is intended for machine processing, not human
   viewing, and as such the format is very rigid.  Implementations MUST
   NOT vary the format by, for example, inserting extra spaces for
   readability, replacing spaces by tabs, including header or title
   lines, or inserting blank lines, or in any other way alter this
   format.  Exactly one space is always required after the set of facts
   (which may be empty).  More spaces may be present on a line if, and
   only if, the pathname presented contains significant spaces.  The set
   of facts must not contain any spaces anywhere inside it.  Facts
   should be provided in each output line only if they both provide
   relevant information about the file named on the same line, and they
   are in the set requested by the user-PI.  See section 7.9 (page 51).
   There is no requirement that the same set of facts be provided for
   each file, or that the facts presented occur in the same order for
   each file.


	 * 
	 * 
	 * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		FileSource dir = null;

		if( context.hasNext() ){
			//  can't use the context because file names may have spaces
			String path = context.getRemainingTokens();
			dir = processor.createNewFile(path);
		} else {
			dir = processor.getCurrentDir();
		} 


		if( dir == null || !dir.exists()){
			processor.reply(REPLY_450_FILE_ACTION_FAILED," Invalid or non existant name");
			return;
		} 

		FileSource [] list = null;

		if( dir.isDirectory()) {
			if( (list = dir.listFiles()) == null ) {
				list = new FileSource[0];
			}
		} else {
			list = new FileSource[1];
			list[0] = dir;
		}


		Socket sock = processor.getDataSocket();

		if( sock == null ) {
			processor.reply(REPLY_425_CANT_OPEN_DATA_CON,"Can't get a data socket");
		} else {
			processor.reply(REPLY_150_FILE_STATUS_OK,"Opening Binary mode data connection for file list");

			StringBuilder buf = new StringBuilder();
			for(int i = 0; i < list.length ; i ++) {
				buf.append(Mlst.formatFile(list[i],processor).trim());
				buf.append('\r');
				buf.append('\n');
			}
			// some clients will complain if the output is empty
			if( buf.length() == 0) {
				buf.append("\r\n");
			}

			Throwable error = null;
			
			try {
				OutputStream out = sock.getOutputStream();			
				out.write(buf.toString().getBytes());
				out.flush();
				out.close();
			} catch (Exception e) {
				error = e;
			} finally {
				try {
					sock.close();
				} catch (Exception e2) {
				}	
			}
			if( error != null) {
				processor.reply(REPLY_551_ACTION_ABORTED,"Transfer error="+error);
			} else {
				processor.reply(REPLY_226_CLOSING_DATA_CON,"Transfer complete");	
			}
			

		}
	}


	/* (non-Javadoc)
	 * @see us.bringardner.net.ftp.server.FeatCommand#getFeatResponse()
	 */
	public String getFeatResponse(FtpRequestProcessor processor) {
		// Does not have a FEAT response.  It is assumed with MLST
		return null;
	}

	@Override
	public IPermission getPermission() {
		return READ_PERMISSION;
	}
}
