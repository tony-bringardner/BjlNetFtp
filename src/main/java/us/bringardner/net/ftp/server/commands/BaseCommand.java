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
 * ~version~V000.01.37-V000.01.35-V000.01.20-V000.01.05-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2004
 *
 */
package us.bringardner.net.ftp.server.commands;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.net.framework.server.ICommandProcessor;
import us.bringardner.net.framework.server.IPermission;
import us.bringardner.net.framework.server.IRequestContext;
import us.bringardner.net.ftp.FTP;
import us.bringardner.net.ftp.server.FtpCommand;
import us.bringardner.net.ftp.server.FtpRequestProcessor;

/**
 * @author Tony Bringardner
 *
 */
public abstract class BaseCommand implements FtpCommand ,FTP {

	private static final long serialVersionUID = 1L;
	
	/** Buffer for directory listings sent on the data connection (BJL-30) */
	public static final int LISTING_BUFFER_SIZE = 64*1024;

	/** Formats one directory entry as a line of a listing (without the line end). */
	@FunctionalInterface
	public interface EntryFormatter {
		String format(FileSource file) throws IOException;
	}

	/**
	 * Send a directory listing (LIST, NLST, MLSD) on the data connection and reply.
	 * <p>
	 * Lines are UTF-8 with CRLF and go through one 64 KB buffer that is flushed once at the
	 * end, so a listing is a few large writes (and TLS records) instead of one write, system
	 * call and TCP segment per file, and nothing builds the whole listing in memory (BJL-30).
	 * <p>
	 * Replies 150 before sending, then 226, or 426 if the data connection fails (the client
	 * closed it, a network error): the control connection stays open (a failed data
	 * connection used to end the whole session). An entry that can't be formatted (e.g. the
	 * file was deleted while listing) is left out and logged, like ls.
	 *
	 * @param processor the session
	 * @param sock the data connection (closed when done)
	 * @param list the entries, null for none
	 * @param formatter makes the line for one entry
	 * @param openMessage text of the 150 reply
	 * @param blankLineIfEmpty send one empty line when there are no entries (some clients
	 * complain about an empty MLSD)
	 * @throws IOException only if a reply can't be sent on the control connection
	 */
	public static void sendListing(FtpRequestProcessor processor, Socket sock, FileSource[] list,
			EntryFormatter formatter, String openMessage, boolean blankLineIfEmpty) throws IOException {
		processor.reply(REPLY_150_FILE_STATUS_OK, openMessage);
		IOException dataError = null;
		int sent = 0;
		try (OutputStream out = new BufferedOutputStream(sock.getOutputStream(), LISTING_BUFFER_SIZE)) {
			if( sock instanceof javax.net.ssl.SSLSocket ) {
				// TLS even for an empty listing (RFC 4217, see FtpServerStream)
				((javax.net.ssl.SSLSocket) sock).startHandshake();
			}
			if( list != null ) {
				for (FileSource file : list) {
					String line;
					try {
						line = formatter.format(file);
					} catch (IOException | RuntimeException e) {
						processor.logError("Can't list "+file, e);
						continue;
					}
					out.write(line.getBytes(StandardCharsets.UTF_8));
					out.write(CRLF);
					sent++;
				}
			}
			if( sent == 0 && blankLineIfEmpty ) {
				out.write(CRLF);
			}
			out.flush();
			// close_notify only, not user_canceled (GnuTLS clients, BJL-2)
			FtpRequestProcessor.shutdownTlsOutput(sock);
		} catch (IOException e) {
			dataError = e;
		} finally {
			try {
				sock.close();
			} catch (IOException e) {
			}
		}
		if( dataError != null ) {
			processor.logDebug("Listing aborted after "+sent+" entries", dataError);
			processor.reply(REPLY_426_CON_CLOSED, "Data connection closed; transfer aborted.");
		} else {
			processor.reply(REPLY_226_CLOSING_DATA_CON, "Transfer complete");
		}
	}

	private static final byte[] CRLF = { '\r', '\n' };

	private String name ;
	private String help = "No help availibl";
	
	
	/**
	 * 
	 */
	public BaseCommand(String command) {
		this.name = command.toUpperCase(java.util.Locale.ROOT);
		help = "No help availibl for "+name;		
	}

	@Override
	public String getName() {
		return name;
	}
	
	
	@Override
	public String getHelp() {
		return help;
	}
	
	
	/*
	 * Check authorization before processing.  Override if not required.
	 * 
	 * @see us.bringardner.net.ftp.server.FtpCommand#process(us.bringardner.net.ftp.server.FtpRequestProcessor, java.lang.String)
	 */
	@Override
	public void execute(ICommandProcessor processor, IRequestContext context) throws IOException {
		
		execute((FtpRequestProcessor)processor,context);
		
	}
	
	
	
	public void setName(String name) {
		this.name = name;
	}

	public void setHelp(String help) {
		this.help = help;
	}

	
	@Override
	public IPermission getPermission() {
		return READ_PERMISSION;
	}
	
	
}
