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
 * ~version~V000.01.43-V000.01.37-V000.01.32-V000.01.05-V000.01.03-V000.01.02-V000.00.03-V000.00.02-V000.00.01-V000.00.00-
 */
/*
 * Created on Nov 25, 2006
 *
 */
package us.bringardner.net.ftp.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import us.bringardner.core.BaseObject;
import us.bringardner.net.ftp.FTP;

public abstract class ClientDataTransferProcess extends BaseObject implements Runnable,FTP {

	private FtpClient client;
	private volatile Socket socket;
	private boolean passive = false;
	private InputStream input;
	private OutputStream output;
	private Thread thread;
	private String name;
	private String host;
	private int port;
	private boolean running;


	public void start() {
		if(!running ) {
			thread = new Thread(this);
			thread.setName(getName()+":"+getPort());
			thread.setDaemon(false);
			thread.start();
		}
	}

	public void stop() {
		running = false;
		thread.interrupt();
	}

	public FtpClient getClient() {
		return client;
	}


	public void setClient(FtpClient client) {
		this.client = client;
		getLogger().setLevel(client.getLogger().getLevel());
	}


	public InputStream getInput() throws UnknownHostException, IOException {
		if( input == null ) {
			Socket socket = getSocket();
			if( socket != null ) {
				input = socket.getInputStream();
			}
		}

		return input;
	}


	public void setInput(InputStream input) {
		this.input = input;
	}


	public OutputStream getOutput() throws UnknownHostException, IOException {
		if( output == null ) {
			Socket socket = getSocket();
			if( socket != null ) {
				output = socket.getOutputStream();
			}
		}
		return output;
	}


	public void setOutput(OutputStream output) {
		this.output = output;
	}


	public boolean isPassive() {
		return passive;
	}


	public void setPassive(boolean passive) {
		this.passive = passive;
	}


	private Socket getServerSocket() throws IOException {
		Socket ret = null;
		FtpClient client = getClient();
		ServerSocketFactory factory = client.getServerSocketFactory();
		ServerSocket svr = factory.createServerSocket(getPort());
		svr.setSoTimeout(client.getTransferTimeout());
		ret = svr.accept();
		if( ret != null ) {
			ret.setSoTimeout(client.getTransferTimeout());
		}


		return ret;
	}

	private Socket getClientSocket() throws UnknownHostException, IOException {
		logDebug("Enter getClientSocket");
		Socket ret = null;
		FtpClient client = getClient();
		SocketFactory factory = client.getSocketFactory();
		if (factory instanceof SSLSocketFactory	) {
			SSLSocket tmp  = (SSLSocket) factory.createSocket(host, port);
			logDebug("getClientSocket switching SSL mode");
			tmp.setUseClientMode(true);
			ret = tmp;
		} else {
			ret = factory.createSocket(getHost(), getPort());
		}
		
		if( ret != null ) {
			ret.setSoTimeout(client.getTransferTimeout());
			ret.setTcpNoDelay(true);
			ret.setReceiveBufferSize(64*1024);
			ret.setSendBufferSize(64*1024);
		}
		logDebug("Exit getClientSocket ret="+ret);
		return ret;
	}


	public Socket getSocket() throws IOException {
		if( socket == null ) {
			synchronized (this) {
				if( socket == null ) {
					Socket tmp = null;
					if(isPassive()) {
						// client has issued PASV command and we are the client
						tmp = getClientSocket();
					} else {
						// Client has issued a PORT command and we are the server
						tmp = getServerSocket();
					}
					
					int bufSz = client.getTransferBufferSize();
					if( bufSz > 0 ) {
						tmp.setSendBufferSize(bufSz);
						tmp.setReceiveBufferSize(bufSz);
					}
					int linger = client.getTransferLinger();
					int timeout = client.getTransferTimeout();
					tmp.setSoTimeout(timeout);
					if( linger > 0 ) {
						tmp.setSoLinger(true, linger);
					} else {
						tmp.setSoLinger(false, linger);
					}
					socket = tmp;
				}
				
			}
		}

		return socket;
	}


	public void setSocket(Socket socket) {
		this.socket = socket;
	}



	public String getHost() {
		return host;
	}



	public void setHost(String host) {
		this.host = host;
	}



	public String getName() {
		return name;
	}



	public void setName(String name) {
		this.name = name;
	}



	public int getPort() {
		return port;
	}



	public void setPort(int port) {
		this.port = port;
	}

	/**
	 * Set the host and port from a string.  The expected format is the same as that 
	 * used by the PASV / PORT commands (ip1,ip2,ip3,ip4,portHiBits,portLowBits).
	 * 
	 * Example (10,129,15,18,200,135) would parameters to host=10.129.15.18 port=51335.
	 * 
	 * @param str
	 */
	public void setHostAndPort(String str) {
		logDebug("Enter setHostAndPort str="+str);
		int idx = str.indexOf('(');
		if( idx > 0 ) {
			str = str.substring(idx+1,str.indexOf(')'));
			logDebug("\tCleaned str="+str);
		}
		String [] parts = str.split(",");
		if( parts.length != 6 ) {
			//System.out.println(getClient().dialog);
			/*We expect the same format that is used in the PASV / PORT commands
			 *   227 Entering Passive Mode (10,129,15,18,200,135)
			 */
			throw new IllegalArgumentException("Invalid format expected 6 comma seperated entries. ("+str+")");
		}
		String tmp = parts[0]+"."+parts[1]+"."+parts[2]+"."+parts[3];
		logDebug("\tsetting host to "+tmp);
		setHost(tmp);
		int port = Integer.parseInt(parts[4]);
		port *= 256;
		port += Integer.parseInt(parts[5]);
		logDebug("\tport="+port);
		setPort(port);
		try {
			InetAddress addr = InetAddress.getByName(tmp);
			if( !addr.isReachable(300)) {
				logDebug("Server sent unreachable address, using server host name instead.");
				setHost(getClient().getHost());
			}
		} catch (Exception e) {
			logDebug("error validating address, using server host name instead.");
			setHost(getClient().getHost());
		}

		logDebug("Exit setHostAndPort str="+str);
	}

	/**
	 * @return host and port number formated as required by the PASV and PORT command (See setHostAndPort).
	 * @throws UnknownHostException
	 */
	public String getFormatedHostAndPort() throws UnknownHostException {
		String host = getHost();
		int port = getPort();

		//  The host may or may not be an ip address.  
		byte [] ip = InetAddress.getByName(host).getAddress();
		short p1 = (short)(port/256);
		short p2 = (short)(port-(p1*256));


		return    ip[0]+","+
		ip[1]+","+
		ip[2]+","+
		ip[3]+","+
		p1+","+
		p2;

	}

	public void close() {
		if( socket != null && !socket.isClosed()) {

			try {
				/*
				 * Socket timeout must be at least as long as the linger
				 * or the timeout will occur before the linger.
				 */
				int linger = client.getTransferLinger();
				if( linger > 0 ) {
					socket.setSoLinger(true, linger);
					socket.setSoTimeout((linger*1000)+1000);
				}
			} catch (SocketException e) {
				//logError("Error setting linger.", e);
			}


			if( output != null ) {
				try {
					output.flush();
					output.close();
				} catch(Exception ex) {}
			}

			if( input != null ) {
				try {
					input.close();
				} catch(Exception ex) {}
			}

			try {
				socket.close();
			} catch(Exception ex) {}
		}

	}    

}
