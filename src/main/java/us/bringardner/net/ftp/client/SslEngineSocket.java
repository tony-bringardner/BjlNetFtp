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
 * ~version~V000.01.56-V000.01.55-V000.01.52-V000.01.51-V000.01.50-V000.01.48-V000.01.46-V000.01.43-V000.01.42-V000.01.40-V000.01.36-V000.01.35-V000.01.33-V000.01.18-V000.01.16-V000.01.15-V000.01.13-V000.01.12-V000.01.11-V000.01.09-V000.01.05-V000.01.03-V000.01.02-V000.01.02-V000.00.03-V000.00.01-V000.00.00-
 */
package us.bringardner.net.ftp.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;

/**
 * A TLS client connection run through an {@link SSLEngine} over a connected plain socket.
 * <p>
 * Used for FTPS data connections (BJL-18). The engine is created with the control
 * connection's host and port, which is the key Java uses to find a TLS session to resume,
 * so data connections resume the control connection's session, as RFC 4217 recommends and
 * servers such as vsftpd (require_ssl_reuse) and FileZilla Server require. An SSLSocket
 * can't do this: it always looks the session up by the port it is actually connected to.
 * <p>
 * The handshake happens on the first read or write (in passive mode the server starts TLS
 * only after it has the transfer command). Closing either stream, or the socket, sends
 * close_notify and closes the connection. As with SSLSocket's default, a server that closes
 * the connection without close_notify ends the data (FTP marks the end of a transfer by
 * closing the data connection).
 */
public final class SslEngineSocket extends Socket {

	private static final ByteBuffer EMPTY = ByteBuffer.allocate(0);
	/** How long close() waits for the server to finish its side (see drainInbound). */
	static final int CLOSE_WAIT_MS = 3000;

	private final Socket raw;
	private final SSLEngine engine;
	private final InputStream rawIn;
	private final OutputStream rawOut;
	private final Object handshakeLock = new Object();
	/** Guards netIn, appIn and inputAtEof. May take writeLock (never the other way round). */
	private final Object readLock = new Object();
	/** Guards netOut and writing to the network. */
	private final Object writeLock = new Object();
	private final InputStream in = new TlsInput();
	private final OutputStream out = new TlsOutput();

	/** Received from the network, not yet unwrapped (kept ready for reading). */
	private ByteBuffer netIn;
	/** Unwrapped application data not yet returned (kept ready for reading). */
	private ByteBuffer appIn;
	/** Wrapped data to send. */
	private ByteBuffer netOut;
	private boolean inputAtEof;
	private volatile boolean handshakeDone;
	private volatile boolean closed;

	/**
	 * @param raw a connected plain socket; closed with this one
	 * @param engine a new engine, created with the host and port whose session should be
	 *   resumed; it is put in client mode
	 */
	public SslEngineSocket(Socket raw, SSLEngine engine) throws IOException {
		this.raw = raw;
		this.engine = engine;
		engine.setUseClientMode(true);
		rawIn = raw.getInputStream();
		rawOut = raw.getOutputStream();
		SSLSession session = engine.getSession();
		netIn = ByteBuffer.allocate(session.getPacketBufferSize());
		netIn.flip();
		appIn = ByteBuffer.allocate(session.getApplicationBufferSize());
		appIn.flip();
		netOut = ByteBuffer.allocate(session.getPacketBufferSize());
	}

	/** @return the TLS session (complete once the first read or write has happened) */
	public SSLSession getSession() {
		return engine.getSession();
	}

	/** Runs the handshake now instead of on first use. */
	public void startHandshake() throws IOException {
		ensureHandshake();
	}

	// ------------------------------------------------------------------ handshake

	private void ensureHandshake() throws IOException {
		if( handshakeDone ) {
			return;
		}
		synchronized (handshakeLock) {
			if( handshakeDone ) {
				return;
			}
			if( closed ) {
				throw new SocketException("Socket is closed");
			}
			engine.beginHandshake();
			HandshakeStatus hs = engine.getHandshakeStatus();
			while( hs != HandshakeStatus.FINISHED && hs != HandshakeStatus.NOT_HANDSHAKING ) {
				switch (hs) {
				case NEED_WRAP:
					hs = wrap(EMPTY).getHandshakeStatus();
					break;
				case NEED_UNWRAP:
				case NEED_UNWRAP_AGAIN:
					synchronized (readLock) {
						SSLEngineResult r = unwrap();
						if( r == null ) {
							throw new SSLHandshakeException("The connection was closed during the TLS handshake");
						}
						hs = r.getHandshakeStatus();
					}
					break;
				case NEED_TASK:
					runTasks();
					hs = engine.getHandshakeStatus();
					break;
				default:
					throw new SSLException("Unexpected handshake status "+hs);
				}
			}
			handshakeDone = true;
		}
	}

	private void runTasks() {
		Runnable task;
		while( (task = engine.getDelegatedTask()) != null ) {
			task.run();
		}
	}

	/** After a read or write: tasks and replies the engine asks for (e.g. a TLS 1.3 KeyUpdate). */
	private void afterOperation(HandshakeStatus hs) throws IOException {
		while( true ) {
			switch (hs) {
			case NEED_TASK:
				runTasks();
				hs = engine.getHandshakeStatus();
				break;
			case NEED_WRAP:
				hs = wrap(EMPTY).getHandshakeStatus();
				break;
			default:
				// anything else is dealt with by the next read
				return;
			}
		}
	}

	// ------------------------------------------------------------------ engine I/O

	/** Wraps from src and sends the result. */
	private SSLEngineResult wrap(ByteBuffer src) throws IOException {
		synchronized (writeLock) {
			while( true ) {
				netOut.clear();
				SSLEngineResult r = engine.wrap(src, netOut);
				switch (r.getStatus()) {
				case BUFFER_OVERFLOW:
					netOut = ByteBuffer.allocate(Math.max(netOut.capacity()*2, engine.getSession().getPacketBufferSize()));
					break;
				case OK:
				case CLOSED:
					netOut.flip();
					if( netOut.hasRemaining() ) {
						rawOut.write(netOut.array(), netOut.arrayOffset()+netOut.position(), netOut.remaining());
						rawOut.flush();
					}
					return r;
				default:
					throw new SSLException("Unexpected wrap status "+r.getStatus());
				}
			}
		}
	}

	/**
	 * Unwraps one record into appIn, reading from the network as needed. Caller holds readLock.
	 * @return the result, or null if the connection ended first
	 */
	private SSLEngineResult unwrap() throws IOException {
		while( true ) {
			appIn.compact();
			SSLEngineResult r;
			try {
				r = engine.unwrap(netIn, appIn);
			} finally {
				appIn.flip();
			}
			switch (r.getStatus()) {
			case OK:
			case CLOSED:
				return r;
			case BUFFER_OVERFLOW:
				ByteBuffer bigger = ByteBuffer.allocate(Math.max(appIn.capacity()*2, appIn.remaining()+engine.getSession().getApplicationBufferSize()));
				bigger.put(appIn);
				bigger.flip();
				appIn = bigger;
				break;
			case BUFFER_UNDERFLOW:
				if( !readFromNetwork() ) {
					return null;
				}
				break;
			default:
				throw new SSLException("Unexpected unwrap status "+r.getStatus());
			}
		}
	}

	/** @return false at end of stream */
	private boolean readFromNetwork() throws IOException {
		int packet = engine.getSession().getPacketBufferSize();
		if( netIn.capacity()-netIn.remaining() < 1 || netIn.capacity() < packet ) {
			ByteBuffer bigger = ByteBuffer.allocate(Math.max(packet, netIn.capacity()*2));
			bigger.put(netIn);
			bigger.flip();
			netIn = bigger;
		}
		netIn.compact();
		int n;
		try {
			n = rawIn.read(netIn.array(), netIn.arrayOffset()+netIn.position(), netIn.remaining());
			if( n > 0 ) {
				netIn.position(netIn.position()+n);
			}
		} finally {
			netIn.flip();
		}
		return n >= 0;
	}

	private int read(byte[] b, int off, int len) throws IOException {
		if( len == 0 ) {
			return 0;
		}
		ensureHandshake();
		synchronized (readLock) {
			while( !appIn.hasRemaining() ) {
				if( inputAtEof ) {
					return -1;
				}
				SSLEngineResult r = unwrap();
				if( r == null ) {
					// closed without close_notify: the end of the data, as SSLSocket treats it
					inputAtEof = true;
					try {
						engine.closeInbound();
					} catch (SSLException e) {
						// "closing inbound before receiving peer's close_notify"
					}
					continue;
				}
				if( r.getStatus() == SSLEngineResult.Status.CLOSED ) {
					inputAtEof = true;
				}
				afterOperation(r.getHandshakeStatus());
			}
			int n = Math.min(len, appIn.remaining());
			appIn.get(b, off, n);
			return n;
		}
	}

	private void write(byte[] b, int off, int len) throws IOException {
		if( closed ) {
			throw new SocketException("Socket is closed");
		}
		ensureHandshake();
		ByteBuffer src = ByteBuffer.wrap(b, off, len);
		while( src.hasRemaining() ) {
			SSLEngineResult r = wrap(src);
			if( r.getStatus() == SSLEngineResult.Status.CLOSED ) {
				throw new SocketException("The TLS connection is closed");
			}
			afterOperation(r.getHandshakeStatus());
		}
	}

	// ------------------------------------------------------------------ streams

	private final class TlsInput extends InputStream {
		@Override
		public int read() throws IOException {
			byte[] one = new byte[1];
			int n;
			while( (n = SslEngineSocket.this.read(one, 0, 1)) == 0 ) {
				// read until a byte or the end
			}
			return n < 0 ? -1 : one[0] & 0xff;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			java.util.Objects.checkFromIndexSize(off, len, b.length);
			return SslEngineSocket.this.read(b, off, len);
		}

		@Override
		public int available() {
			synchronized (readLock) {
				return appIn.remaining();
			}
		}

		@Override
		public void close() throws IOException {
			SslEngineSocket.this.close();
		}
	}

	private final class TlsOutput extends OutputStream {
		@Override
		public void write(int b) throws IOException {
			SslEngineSocket.this.write(new byte[] {(byte) b}, 0, 1);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			java.util.Objects.checkFromIndexSize(off, len, b.length);
			SslEngineSocket.this.write(b, off, len);
		}

		@Override
		public void flush() throws IOException {
			synchronized (writeLock) {
				rawOut.flush();
			}
		}

		@Override
		public void close() throws IOException {
			SslEngineSocket.this.close();
		}
	}

	// ------------------------------------------------------------------ Socket

	@Override
	public InputStream getInputStream() throws IOException {
		if( isClosed() ) {
			throw new SocketException("Socket is closed");
		}
		return in;
	}

	@Override
	public OutputStream getOutputStream() throws IOException {
		if( isClosed() ) {
			throw new SocketException("Socket is closed");
		}
		return out;
	}

	/**
	 * Sends close_notify (if the handshake happened), reads what the server still sends
	 * until it closes (see drainInbound), and closes the connection.
	 */
	@Override
	public void close() throws IOException {
		synchronized (handshakeLock) {
			if( closed ) {
				return;
			}
			closed = true;
		}
		try {
			if( handshakeDone ) {
				engine.closeOutbound();
				while( !engine.isOutboundDone() ) {
					if( wrap(EMPTY).bytesProduced() == 0 ) {
						break;
					}
				}
				drainInbound();
			}
		} catch (IOException | RuntimeException e) {
			// the other side may already be gone
		} finally {
			raw.close();
		}
	}

	/**
	 * Reads until the server closes its side (at most CLOSE_WAIT_MS), discarding any data.
	 * This processes a TLS 1.3 NewSessionTicket the server sent on this connection: Java uses
	 * each ticket once, and an upload never reads, so without this the next data connection
	 * had no session to resume and needed a full handshake, which servers that require
	 * session reuse refuse. FTP servers close the data connection as soon as the transfer ends.
	 */
	private void drainInbound() {
		synchronized (readLock) {
			if( inputAtEof ) {
				return;
			}
			try {
				int old = raw.getSoTimeout();
				raw.setSoTimeout(old > 0 ? Math.min(old, CLOSE_WAIT_MS) : CLOSE_WAIT_MS);
				while( true ) {
					// discard application data
					appIn.clear();
					appIn.flip();
					SSLEngineResult r = unwrap();
					if( r == null || r.getStatus() == SSLEngineResult.Status.CLOSED ) {
						break;
					}
					if( r.getHandshakeStatus() == HandshakeStatus.NEED_TASK ) {
						runTasks();
					}
				}
			} catch (IOException | RuntimeException e) {
				// timed out, or the connection is gone: nothing more to read
			} finally {
				inputAtEof = true;
			}
		}
	}

	@Override
	public boolean isClosed() {
		return closed || raw.isClosed();
	}

	@Override
	public void shutdownOutput() throws IOException {
		if( handshakeDone ) {
			engine.closeOutbound();
			while( !engine.isOutboundDone() ) {
				if( wrap(EMPTY).bytesProduced() == 0 ) {
					break;
				}
			}
		}
		raw.shutdownOutput();
	}

	@Override
	public void shutdownInput() throws IOException {
		raw.shutdownInput();
	}

	@Override
	public void connect(SocketAddress endpoint, int timeout) throws IOException {
		throw new SocketException("Already connected");
	}

	@Override
	public void bind(SocketAddress bindpoint) throws IOException {
		throw new SocketException("Already bound");
	}

	@Override public boolean isConnected() { return raw.isConnected(); }
	@Override public boolean isBound() { return raw.isBound(); }
	@Override public boolean isInputShutdown() { return raw.isInputShutdown(); }
	@Override public boolean isOutputShutdown() { return raw.isOutputShutdown(); }
	@Override public InetAddress getInetAddress() { return raw.getInetAddress(); }
	@Override public InetAddress getLocalAddress() { return raw.getLocalAddress(); }
	@Override public int getPort() { return raw.getPort(); }
	@Override public int getLocalPort() { return raw.getLocalPort(); }
	@Override public SocketAddress getRemoteSocketAddress() { return raw.getRemoteSocketAddress(); }
	@Override public SocketAddress getLocalSocketAddress() { return raw.getLocalSocketAddress(); }
	@Override public void setSoTimeout(int timeout) throws SocketException { raw.setSoTimeout(timeout); }
	@Override public int getSoTimeout() throws SocketException { return raw.getSoTimeout(); }
	@Override public void setSoLinger(boolean on, int linger) throws SocketException { raw.setSoLinger(on, linger); }
	@Override public int getSoLinger() throws SocketException { return raw.getSoLinger(); }
	@Override public void setTcpNoDelay(boolean on) throws SocketException { raw.setTcpNoDelay(on); }
	@Override public boolean getTcpNoDelay() throws SocketException { return raw.getTcpNoDelay(); }
	@Override public void setKeepAlive(boolean on) throws SocketException { raw.setKeepAlive(on); }
	@Override public boolean getKeepAlive() throws SocketException { return raw.getKeepAlive(); }
	@Override public void setReceiveBufferSize(int size) throws SocketException { raw.setReceiveBufferSize(size); }
	@Override public int getReceiveBufferSize() throws SocketException { return raw.getReceiveBufferSize(); }
	@Override public void setSendBufferSize(int size) throws SocketException { raw.setSendBufferSize(size); }
	@Override public int getSendBufferSize() throws SocketException { return raw.getSendBufferSize(); }

	@Override
	public String toString() {
		return "SslEngineSocket["+raw+", "+engine.getSession().getProtocol()+"]";
	}
}
