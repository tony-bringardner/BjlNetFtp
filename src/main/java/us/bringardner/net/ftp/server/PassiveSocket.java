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
 * ~version~V000.01.49-V000.01.46-V000.01.33-V000.01.11-V000.01.02-V000.00.01-V000.00.00-
 */
package us.bringardner.net.ftp.server;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.net.ServerSocketFactory;

/**
 * The server side of a passive (PASV / EPSV) data connection.
 * <p>
 * {@link #open(FtpRequestProcessor)} binds the listening socket <b>before</b> the 227/229
 * reply is sent, so the advertised port is always valid and there is no startup wait.
 * The connection is accepted lazily, on the control thread, the first time the data socket
 * is needed (the client's connect completes in the listen backlog in the meantime). 
 * A passive socket is used for exactly one data connection: the listening socket is closed
 * as soon as a connection is accepted, when the accept times out, or when
 * {@link #abor()} is called (a new PASV/PORT, ABOR or end of session).
 */
public  class PassiveSocket {

	/** Kept for compatibility, no longer used. */
	public static final int MAX_ERRORS = 4;

	private static int minControlPort = 10333;
	private static int maxControlPort = 65333;

	/** The next port to try (rotates through [minControlPort, maxControlPort]). */
	private static int controlPort = minControlPort;

	private final FtpRequestProcessor processor;
	private final InetAddress advertisedAddress;
	private final int port;
	private ServerSocket serverSocket;
	private Socket dataSocket;
	private boolean closed = false;

	/**
	 * Bind a listening socket on a free port in the configured range.
	 *
	 * @param processor the control connection that owns this data connection
	 * @return a bound PassiveSocket
	 * @throws IOException if no port in the range could be bound
	 */
	public static PassiveSocket open(FtpRequestProcessor processor) throws IOException {
		InetAddress local = processor.getConnection().getSocket().getLocalAddress();
		ServerSocketFactory factory = processor.getServerSocketFactory();

		int min, max;
		synchronized (PassiveSocket.class) {
			min = minControlPort;
			max = maxControlPort;
		}
		int attempts = max - min + 1;
		IOException last = null;
		for(int i = 0; i < attempts; i++) {
			int port = nextControlPort();
			ServerSocket svr = null;
			try {
				svr = factory.createServerSocket(port, 1, local);
				return new PassiveSocket(processor, svr, advertisedAddress(processor, local));
			} catch (IOException e) {
				// Port in use (or not permitted), try the next one.
				last = e;
				if( svr != null ) {
					try {
						svr.close();
					} catch (IOException e1) {
					}
				}
			}
		}
		throw new IOException("No free passive port in range "+min+"-"+max, last);
	}

	private static InetAddress advertisedAddress(FtpRequestProcessor processor, InetAddress local) {
		String tmp = System.getProperty(FtpServer.EXTERNAL_ADDRESS_PROP);
		if( tmp != null ) {
			try {
				return InetAddress.getByName(tmp);
			} catch (UnknownHostException e) {
				processor.logError("Can't find address for external ("+tmp+")");
			}
		}
		return local;
	}

	private PassiveSocket(FtpRequestProcessor processor, ServerSocket svr, InetAddress advertised) throws SocketException {
		this.processor = processor;
		this.serverSocket = svr;
		this.port = svr.getLocalPort();
		this.advertisedAddress = advertised;
		int timeout = processor.getActivityTimeOut();
		svr.setSoTimeout(timeout > 0 ? timeout : FtpServer.DEFAULT_DATA_TIMEOUT);
	}

	/**
	 * Close the listening socket and any accepted data socket.
	 */
	public synchronized void abor() {
		closed = true;
		closeServerSocket();
		if( dataSocket != null ) {
			try {
				dataSocket.close();
			} catch(Exception ex) {
				processor.logDebug("Error closing passive data socket", ex);
			}
			dataSocket = null;
		}
	}

	private void closeServerSocket() {
		if( serverSocket != null ) {
			try {
				serverSocket.close();
			} catch (IOException e) {
			}
			serverSocket = null;
		}
	}

	/**
	 * Accept the client's data connection (waits up to the processor's activity timeout).
	 *
	 * @return the connected data socket, or null if the client did not connect in time
	 * or this passive socket was closed.
	 */
	public synchronized Socket getDataSocket() {
		if( dataSocket != null || closed ) {
			return dataSocket;
		}
		try {
			int timeout = serverSocket.getSoTimeout();
			long deadline = System.currentTimeMillis() + timeout;
			while( dataSocket == null ) {
				Socket s = serverSocket.accept();
				if( processor.isAllowedPassivePeer(s.getInetAddress()) ) {
					dataSocket = s;
				} else {
					// RFC 2577: don't let a third party steal the data connection
					processor.logInfo("Rejected passive data connection from "+s.getInetAddress().getHostAddress()
							+" (control connection is from "+processor.getConnection().getSocket().getInetAddress().getHostAddress()+")");
					try {
						s.close();
					} catch (IOException e) {
					}
					long remaining = deadline - System.currentTimeMillis();
					if( remaining <= 0 ) {
						throw new SocketTimeoutException("No valid passive data connection");
					}
					serverSocket.setSoTimeout((int)remaining);
				}
			}
			dataSocket.setSoTimeout(processor.getActivityTimeOut());
		} catch (SocketTimeoutException e) {
			processor.logDebug("Timed out waiting for passive data connection on port "+port);
		} catch (IOException e) {
			if( !closed ) {
				processor.logError("Error accepting passive data connection on port "+port, e);
			}
		} finally {
			// One connection per PASV.
			closeServerSocket();
		}
		return dataSocket;
	}

	public static synchronized int getMinControlPort() {
		return minControlPort;
	}

	public static synchronized void setMinControlPort(int minControlPort) {
		PassiveSocket.minControlPort = minControlPort;
		if( controlPort < minControlPort) {
			controlPort = minControlPort;
		}
	}

	public static synchronized int getMaxControlPort() {
		return maxControlPort;
	}

	public static synchronized void setMaxControlPort(int maxControlPort) {
		PassiveSocket.maxControlPort = maxControlPort;
		if( controlPort > maxControlPort) {
			controlPort = minControlPort;
		}
	}

	/**
	 * @return the next port to try, always within [minControlPort, maxControlPort].
	 */
	public static synchronized int getControlPort() {
		return nextControlPort();
	}

	private static synchronized int nextControlPort() {
		if( controlPort < minControlPort || controlPort > maxControlPort ) {
			controlPort = minControlPort;
		}
		int ret = controlPort;
		controlPort = ret >= maxControlPort ? minControlPort : ret + 1;
		return ret;
	}

	static synchronized void setControlPort(int newCtrPort) {
		controlPort = newCtrPort;
		if(controlPort < minControlPort) {
			minControlPort = controlPort;
		} else if( controlPort > maxControlPort) {
			maxControlPort = controlPort;
		}
	}

	/**
	 * @return the port this passive socket is listening on
	 */
	public int getPort() {
		return port;
	}

	/**
	 * @return true if the advertised address can be expressed in a PASV (IPv4) reply.
	 */
	public boolean isIpv4() {
		return advertisedAddress instanceof Inet4Address;
	}

	/**
	 * @return the PASV address in h1,h2,h3,h4,p1,p2 form
	 */
	@Override
	public String toString() {
		byte [] b = advertisedAddress.getAddress();
		StringBuilder ret = new StringBuilder();
		if( b.length == 4 ) {
			for(byte v : b) {
				ret.append(v & 0xff).append(',');
			}
		}
		return ret.append(port / 256).append(',').append(port % 256).toString();
	}
}
