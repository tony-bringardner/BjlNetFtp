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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import us.bringardner.core.BaseThread;
import us.bringardner.net.ftp.FTP;

/**
 * A thread to transfer data either direction so the processor is not blocked.
 * <p>
 * The processor sends the preliminary (150) reply <b>before</b> this thread is started
 * (see {@link FtpRequestProcessor.StreamController#start}); this thread sends exactly one
 * final reply:
 * <ul>
 * <li>226 when all data was transferred (and the {@link CompletionHandler}, if any, succeeded)</li>
 * <li>426 + 226 when the transfer was aborted with ABOR</li>
 * <li>426 when the data connection failed or was idle longer than the data timeout</li>
 * <li>451 when a local (file system) error occurred</li>
 * </ul>
 * A watchdog closes the data socket if no data moves for {@code timeout} milliseconds,
 * so a transfer can never hang forever on a dead peer (this covers blocked socket
 * writes, which SO_TIMEOUT does not).
 */
public class FtpServerStream extends BaseThread {

	/**
	 * Called on the transfer thread after the data has been copied and both streams are
	 * closed, before the final reply is sent. Used by STOR to move a temporary file into
	 * place on success or delete it on failure.
	 */
	public interface CompletionHandler {
		/**
		 * @param success true if all data was transferred.
		 * @throws IOException if success was true but the result could not be committed.
		 *   The client will receive a 451 reply.
		 */
		void transferComplete(boolean success) throws IOException;
	}

	private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "FtpServerStream-watchdog");
		t.setDaemon(true);
		return t;
	});

	public final Object lock = new Object();
	final FtpRequestProcessor processor;
	private final InputStream input;
	private final OutputStream output;
	private final Socket socket;
	/** true = data flows from the socket to local storage (STOR/APPE) */
	private final boolean upload;
	private final int timeout;
	private final CompletionHandler completionHandler;

	private volatile long bytesTransfered = 0;
	private volatile long lastProgress;
	private volatile boolean aborted = false;
	private volatile boolean timedOut = false;
	/** true once the final reply has been sent (or this stream will never run). */
	private volatile boolean finished = false;
	private IOException error;
	/** true if the error came from the local (file) side, rather than the network side */
	private boolean localError = false;

	/**
	 * Transfer all data from in to out.
	 * Only one (in or out) comes from the socket.
	 * The socket is here so we can close it on an abort.
	 */
	public FtpServerStream(FtpRequestProcessor processor, InputStream in , OutputStream out,Socket sock) {
		this(processor, in, out, sock, false, null);
	}

	/**
	 * @param processor the processor that owns the control connection
	 * @param in source of the data
	 * @param out destination of the data
	 * @param sock the data socket (the source for an upload, the destination for a download)
	 * @param upload true if data flows from the socket to local storage
	 * @param handler optional callback invoked after the copy (may be null)
	 */
	public FtpServerStream(FtpRequestProcessor processor, InputStream in , OutputStream out,Socket sock, boolean upload, CompletionHandler handler) {
		this.processor = processor;
		this.input = in;
		this.output = out;
		this.socket = sock;
		this.upload = upload;
		this.completionHandler = handler;
		int tmo = processor.getActivityTimeOut();
		this.timeout = tmo > 0 ? tmo : FtpServer.DEFAULT_DATA_TIMEOUT;
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
			logDebug("Asci mode in FtpServerStream ... ignored");
		}
	}


	/**
	 * This constructor is only used by default for processor.
	 * and never started.
	 */
	public FtpServerStream() {
		processor = null;
		input = null;
		output = null;
		socket = null;
		upload = false;
		completionHandler = null;
		timeout = FtpServer.DEFAULT_DATA_TIMEOUT;
		stopping=started = true;
		running = false;
		finished = true;
	}

	/**
	 * @return true from the time the transfer is handed to the controller until its final reply has been sent.
	 */
	public boolean isActive() {
		return !finished;
	}

	public long getBytesTransfered() {
		return bytesTransfered;
	}

	/**
	 * Abort the transfer (ABOR). Closing the socket unblocks any pending read or write.
	 */
	public void abort() {
		if( !finished && !aborted) {
			aborted = true;
			stop();
			closeQuietly(socket);
		}
	}

	/**
	 * Called instead of {@link #start()} when the transfer is refused (another transfer
	 * is already running). Releases the local stream and lets the handler clean up.
	 * The data socket is left alone because it may be shared with the running transfer.
	 */
	void discard() {
		closeQuietly(upload ? output : input);
		if( completionHandler != null ) {
			try {
				completionHandler.transferComplete(false);
			} catch (Exception e) {
				processor.logError("Error discarding transfer", e);
			}
		}
		finished = true;
	}

	@Override
	public void run() {
		started = running = true;
		byte [] buffer = new byte[processor.getBufferSize()];
		boolean done = false;
		lastProgress = System.currentTimeMillis();

		try {
			// Blocking reads with a real timeout (previously 100ms + ignore, which never timed out)
			socket.setSoTimeout(timeout);
		} catch (SocketException e) {
			processor.logDebug("Can't set socket timeout", e);
		}

		long period = Math.max(100, Math.min(1000, timeout / 4));
		ScheduledFuture<?> watchdog = WATCHDOG.scheduleAtFixedRate(() -> {
			if( !stopping && System.currentTimeMillis() - lastProgress > timeout ) {
				timedOut = true;
				stop();
				closeQuietly(socket);
			}
		}, period, period, TimeUnit.MILLISECONDS);

		try {
			while(!stopping && !done) {
				int got;
				try {
					got = input.read(buffer);
				} catch (SocketTimeoutException e) {
					timedOut = true;
					throw e;
				} catch (IOException e) {
					localError = !upload;
					throw e;
				}
				if( got < 0 ) {
					done = true;
				} else if( got > 0 ) {
					try {
						output.write(buffer, 0, got);
					} catch (IOException e) {
						localError = upload;
						throw e;
					}
					bytesTransfered += got;
					lastProgress = System.currentTimeMillis();
				}
			}
			if( done ) {
				try {
					output.flush();
				} catch (IOException e) {
					localError = upload;
					throw e;
				}
			}
		} catch (IOException e) {
			error = e;
			if( aborted || timedOut ) {
				processor.logDebug("Transfer stopped (aborted="+aborted+" timedOut="+timedOut+")", e);
			} else {
				processor.logError("Error in data transfer after "+bytesTransfered+" bytes", e);
			}
		} finally {
			watchdog.cancel(false);
		}

		boolean success = done && error == null && !aborted && !timedOut;
		finish(success);
	}

	/**
	 * Close everything, run the completion handler and send the single final reply.
	 */
	private void finish(boolean success) {
		try {
			if( success ) {
				try {
					processor.setLinger(socket);
				} catch (Exception e) {
					// ignore
				}
			}
			// Close the local side first and check for errors (e.g. disk full on close)
			Closeable local = upload ? output : input;
			Closeable remote = upload ? input : output;
			try {
				local.close();
			} catch (IOException e) {
				if( success ) {
					success = false;
					localError = true;
					error = e;
					processor.logError("Error closing local stream", e);
				}
			}
			closeQuietly(remote);
			closeQuietly(socket);

			String commitError = null;
			if( completionHandler != null ) {
				try {
					completionHandler.transferComplete(success);
				} catch (Exception e) {
					processor.logError("Error completing transfer", e);
					commitError = e.getMessage();
					success = false;
				}
			}

			synchronized (lock) {
				/*
				 * Mark the transfer finished BEFORE sending the final reply. The client may send
				 * its next command (e.g. PASV + RETR) the instant it reads the 226; if we are still
				 * "active" at that point the next transfer is refused with 425.
				 * A concurrent ABOR waits for this lock, so its reply still follows ours.
				 */
				finished = true;
				try {
					if( aborted ) {
						/*
						 * RFC 959: the server aborts the FTP service in progress and closes the data
						 * connection, returning a 426 reply to indicate that the service request
						 * terminated abnormally.  The server then sends a 226 reply, indicating that
						 * the abort command was successfully processed.
						 */
						processor.reply(FTP.REPLY_426_CON_CLOSED,"Transfer aborted. "+bytesTransfered+" bytes transfered");
						processor.reply(FTP.REPLY_226_CLOSING_DATA_CON,"ABOR command successful");
					} else if( success ) {
						processor.reply(FTP.REPLY_226_CLOSING_DATA_CON,"Transfer complete. "+bytesTransfered+" bytes transfered");
					} else if( commitError != null ) {
						processor.reply(FTP.REPLY_451_ACTION_ABORTED_LOCAL_ERROR,"Transfer failed, local error: "+commitError);
					} else if( timedOut ) {
						processor.reply(FTP.REPLY_426_CON_CLOSED,"Data connection idle for more than "+(timeout/1000.0)+" seconds; transfer aborted after "+bytesTransfered+" bytes");
					} else if( localError ) {
						processor.reply(FTP.REPLY_451_ACTION_ABORTED_LOCAL_ERROR,"Transfer failed, local error: "+(error == null ? "unknown" : error.getMessage()));
					} else {
						processor.reply(FTP.REPLY_426_CON_CLOSED,"Data connection failed; transfer aborted after "+bytesTransfered+" bytes"+(error == null ? "" : ": "+error.getMessage()));
					}
				} finally {
					finished = true;
				}
			}
		} catch (Throwable e) {
			processor.logError("Error completing transfer", e);
		} finally {
			finished = true;
			running = false;
		}
	}

	private static void closeQuietly(Closeable c) {
		if( c != null ) {
			try {
				c.close();
			} catch (Exception e) {
				// ignore
			}
		}
	}
}
