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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import javax.net.ServerSocketFactory;
/**
 * This class is used to implement the DTP in PASIVE mode
 * All other DTP functions are provided by the ReqProcessor
 * 
 */
public  class PassiveSocket implements Runnable {
	
		
    public static final int MAX_ERRORS = 4;

    private static int minControlPort = 10333;
    private static int maxControlPort = 65333;
    
    private static int controlPort = minControlPort;



    private String [] hostAry;
    private 	int port;
    private 	Socket dataSocket;
    private boolean running = false;
    private boolean error = false;
    private boolean complete = false;
    private FtpRequestProcessor prosessor ;
    private StringBuffer debug = new StringBuffer();

    /**
     * PassiveSocket constructor comment.
     */
    public PassiveSocket(FtpRequestProcessor processor)	throws java.net.UnknownHostException
    {
        this.prosessor = processor;
        InetAddress add = prosessor.getConnection().getSocket().getLocalAddress();
        String tmp = System.getProperty(FtpServer.EXTERNAL_ADDRESS_PROP);
		if( tmp != null ) {
			try {
				add = InetAddress.getByName(tmp);
			} catch (UnknownHostException e) {
				processor.logError("Can't find address for external ("+tmp+")");
			}
		}

        setHost(add);
        setPort(getControlPort());
    }

    public void abor()
    {
        running = false;
        if( dataSocket != null ) {
            try {
            	dataSocket.close(); 
            	dataSocket = null;
            	} catch(Exception ex) {}
        }
    }
    
    
    
    
    public static int getMinControlPort() {
		return minControlPort;
	}

	public synchronized static void setMinControlPort(int minControlPort) {
		PassiveSocket.minControlPort = minControlPort;
		if( controlPort < minControlPort) {
			controlPort = minControlPort;
		}
	}

	public static int getMaxControlPort() {
		return maxControlPort;
	}

	public synchronized static void setMaxControlPort(int maxControlPort) {
		PassiveSocket.maxControlPort = maxControlPort;
		if( controlPort > maxControlPort) {
			controlPort = maxControlPort;
		}
	}

	/**
     * Creation date: (9/5/01 8:53:20 AM)
     * @return int
     */
    public synchronized static int getControlPort() 
    {
    	
    	if( controlPort ++ > maxControlPort) {
    		controlPort = minControlPort;
    	}
        
        return controlPort;
    }
    
    /**
     * Creation date: (9/5/01 11:14:50 AM)
     * @return java.net.Socket
     */
    public java.net.Socket getDataSocket() {
        prosessor.logDebug("Enter PassiveSocket.getDataSocket get dataSocket running="+running+" comp="+complete+" err="+error);
        while(!isComplete()) {
            prosessor.logDebug("PassiveSocket.getDataSocket waiting to compete.");
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) { }
        }
        prosessor.logDebug("Enter PassiveSocket.getDataSocket haveSocket="+(dataSocket==null?"No":"Yes"));
        return dataSocket;
    }

    public  void run()
    {
        prosessor.logDebug("PasvSocket.run -- start");

        ServerSocketFactory factory = prosessor.getServerSocketFactory();
        
        ServerSocket svrSock;
        int timeout = prosessor.getActivityTimeOut();

        try {

            //svrSock = factory.createServerSocket(port,4,host);
            svrSock = factory.createServerSocket(port);            
            svrSock.setSoTimeout(timeout);

        } catch (IOException ex) {
            error  = true;
            complete = true;
            debug.append("Error creating server socket:"+ex);
            prosessor.logError("Error creating server socket",ex);
            return;
        }


        int errCnt = 0;
        prosessor.logDebug("PasvSocket.run -- have serverSocket");
        IOException [] errors = new IOException[MAX_ERRORS];
        running = true;

        while(running && dataSocket==null && errCnt < errors.length) {
            try {
                prosessor.logDebug("PasvSocket.run -- before accept.");
                dataSocket =  svrSock.accept();
                prosessor.logDebug("PasvSocket.run -- socket accepted.");
            } catch (IOException ex2) {
                prosessor.logDebug("PasvSocket.run -- accept error ex="+ex2);
                prosessor.logError("Error in PasvSocket run errCnt="+errCnt,ex2);
                errors[errCnt++] = ex2;
            }
        }
        running = false;
        complete = true;
        if( dataSocket == null ) {
            //  Could not get a Socket
            prosessor.logDebug("Could not get data Sokect due to errors ("+errors[errors.length-1]+")");
            debug.append("Could not get data Sokect due to errors ("+errors[errors.length-1]+")");
        } else {
            //  Was able to get a socket
            try {
                dataSocket.setSoTimeout(prosessor.getActivityTimeOut());
            } catch (SocketException ex2) {
                prosessor.logError("Error in settint timeout in PasvSocket =",ex2);
            }
        }
        prosessor.logDebug("PasvSocket.run -- end have socket="+(dataSocket==null?"No":"Yes"));

    }
    
    public boolean isError() {
        return error;
    }

    public boolean isComplete() {
        return complete;
    }

    public int getPort() {
    	return port;
    }
    
    /**
     * Creation date: (9/5/01 8:53:20 AM)
     * @param newCtrPort int
     */
    static synchronized void setControlPort(int newCtrPort) {
        controlPort = newCtrPort;
        if(controlPort < minControlPort) {
        	minControlPort = controlPort;
        } else if( controlPort > maxControlPort) {
        	maxControlPort = controlPort;
        }
    }
    
    /**
 Set the host array based on the int val
     */
    public void setHost(InetAddress addr)
    {

        // Should work out to 4,109
        //host = addr;
        byte [] b = addr.getAddress();
        hostAry = new String[b.length];
        for(int i=0; i< b.length; i++ ) {
            hostAry[i] = ""+((int)b[i]&0xff);
        }


    }
    /**
 Set the port array based on the int val
     */
    public void setPort(int val)
    {

        port = val;

    }
    public String toString()
    {
        short p1 = (short)(port/256);
        short p2 = (short)(port-(p1*256));


        return
        hostAry[0]+","+
        hostAry[1]+","+
        hostAry[2]+","+
        hostAry[3]+","+
        p1+","+
        p2;

    }

    public boolean isRunning() {
        return running;
    }
}
