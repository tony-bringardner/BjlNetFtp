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
 * ~version~V000.01.35-V000.01.05-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.net.ftp.server.commands;

import java.io.IOException;

import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.server.FeatCommand;
import us.bringardner.net.ftp.server.FtpRequestProcessor;

/**
 * @author Tony Bringardner
 *
 */
public class Tvfs extends NoAuthReqBaseCommand implements FeatCommand {

	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	public Tvfs() {
		super(TVFS);
	
	}

	/* 
	 *
	 * This object does nothing more than indicate to Feat that 
	 * the server supports TVFS.  The following describes TVFS.
	 *  
	 * Internet Draft        draft-ietf-ftpext-mlst-16.txt       September 2002
	 * http://www.ietf.org/internet-drafts/draft-ietf-ftpext-mlst-16.txt
	 * 
	 * 6. A Trivial Virtual File Store (TVFS)

   Traditionally, FTP has placed almost no constraints upon the file
   store (NVFS) provided by a server.  This specification does not alter
   that.  However, it has become common for servers to attempt to
   provide at least file system naming conventions modeled loosely upon
   those of the UNIX(TM) file system.  That is, a tree structured file
   system, built of directories, each of which can contain other
   directories, or other kinds of files, or both.  Each file and
   directory has a name relative to the directory that contains it,
   except for the directory at the root of the tree, which is contained
   in no other directory, and hence has no name of its own.

   That which has so far been described is perfectly consistent with the
   standard FTP NVFS and access mechanisms.  The "CWD" command is used
   to move from one directory to an embedded directory.  "CDUP" may be
   provided to return to the parent directory, and the various file
   manipulation commands ("RETR", "STOR", the rename commands, etc) are
   used to manipulate files within the current directory.

   However, it is often useful to be able to reference files other than
   by changing directories, especially as FTP provides no guaranteed
   mechanism to return to a previous directory.  The Trivial Virtual
   File Store (TVFS), if implemented, provides that mechanism.



	 * 
	 * @see us.bringardner.net.ftp.server.FtpCommand#execute(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	public void execute(FtpRequestProcessor processor, IRequestContext context) throws IOException {
		processor.reply(REPLY_200_OK,TVFS+" not really a command.  Only here to indicate that it is supported.");
	}

	/* (non-Javadoc)
	 * @see us.bringardner.net.ftp.server.FeatCommand#getFeatResponse()
	 */
	public String getFeatResponse(FtpRequestProcessor processor) {

		return TVFS;
	}

}
