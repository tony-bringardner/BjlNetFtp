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
 * ~version~V000.01.46-V000.01.34-V000.01.12-V000.01.09-
 */
/*
 * Created on Dec 12, 2006
 *
 */
package us.bringardner.net.ftp.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import us.bringardner.core.BaseThread;

/**
 * A thread to transfer data either direction so the processor is not blocked.
 */
public class FtpServerStream extends BaseThread {

	public Object lock = new Object();
	FtpRequestProcessor processor;
	InputStream input; 
	OutputStream output;
	IOException error;
	long bytesTransfered = 0;
	boolean aborted = false;
	private Socket socket;

	/**
	 * Transfer all data from in to out.
	 * Only one (in or out) comes from the socket.
	 * The socket is here so we can close it on an abort.
	 *  
	 * @param processor
	 * @param in
	 * @param out
	 * @param sock
	 */
	public FtpServerStream(FtpRequestProcessor processor, InputStream in , OutputStream out,Socket sock) {
		this.processor = processor;
		input = in;
		output = out;
		socket = sock;
		setName("FtpServerStream");
		/**
		 * Over the years ASACII mode has lost it's meaning. Originally the server side 
		 * of an FTP conversation was most likely an IBM mainframe using EBCDIC character encoding for text. 
		 * Since we don't support any character set than ASCII so that's a mute point.  The one remaining issue
		 * in the "Line-Ending Problem". There is a good summation https://www.rfc-editor.org/rfc/rfc5198#appendix-C. 
		 * 
		 * My opinion is that if the users client OS uses CRLC or LF, the users will "MOST LIKLEY" want to 
		 * preserve that format. So in this implementation, ASII mode is just ignored.
		 * 
		 */
		
		if( processor.isAsciiMode()) {
			logInfo("Asci mode in FtpServerStream ... ignored");
		}
	}


	/**
	 * This constructor is only used by default for processor.
	 * and never started.
	 */
	public FtpServerStream() {
		stopping=started = true;
		running = false;
	}


	public void abort() {
		if( !stopping && !aborted) {
			aborted = true;
			stop();
			try {
				socket.close();
			} catch (Exception e) {
			}
		}
	}

	@Override
	public void run() {
		byte [] buffer = new byte[processor.getBufferSize()];

		try {
			//  set timeout to a very small value so an close does not hang
			socket.setSoTimeout(100);
		} catch (SocketException e) {
			logDebug("Can't set socket timeout", e);
		}
		
		started = running = true;
		boolean done = false;
		
		while(!stopping && !done) {
			// Two try-catch because I want to know if it was input or output that failed.

			int got = 0;
			try {
				got = input.read(buffer);
				if( got < 0 ) {
					done = true;
				} 
			} catch(SocketTimeoutException e) {
			} catch (IOException e) {
				stopping=true;
				error = e;
				if(e.getMessage().contains("closed")) {
					processor.logDebug("Error reading in server stream", e);
				} else {
					processor.logError("Error reading in server stream", e);
				}				
			}
			if( !done && got > 0 ) {
				try {
					output.write(buffer, 0, got);
					bytesTransfered += got;
				} catch(SocketTimeoutException e) {
				} catch (IOException e) {
					stopping = true;
					error = e;
					if(e.getMessage().contains("closed")) {
						processor.logDebug("Error writing in server stream", e);
					} else {
						processor.logError("Error writing in server stream", e);
					}
				}				
			}
		}


		/*
		 * It seems that SoTimeout overrides SoLinger.
		 * So, when we close, we set the timeout to the same
		 * value as the linger. java.net.SocketException: Socket closed
		 */
		try {

			synchronized (lock) {
				
				
				if( aborted ) {
					/*
	 			the server aborts the FTP service in
	            progress and closes the data connection, returning a 426
	            reply to indicate that the service request terminated
	            abnormally.  The server then sends a 226 reply,
	            indicating that the abort command was successfully
	            processed.
					 */
					processor.reply(us.bringardner.net.ftp.FTP.REPLY_426_CON_CLOSED,"transfer aborted. "+bytesTransfered+" bytes transfered");
					processor.reply(us.bringardner.net.ftp.FTP.REPLY_226_CLOSING_DATA_CON,"abort ok");
					running = false;
					return;
				} 

				try {
					processor.setLinger(socket);
					input.close();
					logDebug("Closed in");
					output.close();
					logDebug("Closed out");
				} catch (Exception e) {
					// ignore any error here
				}


				if( done ) {
					processor.reply(us.bringardner.net.ftp.FTP.REPLY_200_OK,"02 Ok "+bytesTransfered+" transfered  a="+aborted);
				} else {
					if( error != null ) {
						throw error;
					}				
				} 
				running = false;
			}
			logDebug("After response");

		} catch (Throwable e) {
			processor.logError("Error stopping transfer", e);
			try {
				processor.reply(us.bringardner.net.ftp.FTP.REPLY_551_ACTION_ABORTED," error ending transfer = "+error);
			} catch (IOException e1) {
			}
		} finally {
			running = false;
		}


	}

}
