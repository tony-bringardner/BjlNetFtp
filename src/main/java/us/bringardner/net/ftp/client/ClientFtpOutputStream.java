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
 * ~version~V000.01.51-V000.01.09-V000.01.03-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 15, 2006
 *
 */
package us.bringardner.net.ftp.client;

import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketTimeoutException;

import us.bringardner.net.ftp.FTP;

public class ClientFtpOutputStream extends OutputStream implements FTP {
    //  This client is a private connection just for downloading one file.
    private FtpClient client;
    private String path;
    private OutputStream out ;
    private ClientDataTransferProcess dtp ;
    private boolean ascii;
    private boolean append = false;
    private boolean closed = false;
    
    public ClientFtpOutputStream(String path, FtpClient client, boolean ascii, boolean append) throws IOException {
        this.path = path;
        this.client = client;
        this.ascii = ascii;
        this.append = append;
        startUpload();
    }

    private void startUpload() throws IOException {
        if( ascii ) {
            client.setAsciiType();
        } else {
            client.setImageType();
        }
        
        dtp = client.getDataTransferProcess();

        out = dtp.getOutput();
        ClientFtpResponse res = null;
        
        if( append ) {
            res = client.executeCommand(APPE, path);
            if( !res.isPositivePreliminay()) {
                throw new IOException ("Error invalid respones to APPE = "+res);
            }
        } else {
            res = client.executeCommand(STOR, path);
            if( !res.isPositivePreliminay()) {
                throw new IOException ("Error invalid respones to STOR = "+res);
            }
        }
        
        closed = false;
    }

    public void write(int b) throws IOException {
        out.write(b);
    }

    public void close() throws IOException {
    	if( !closed ) {
    		completeUpload();
    		closed = true;
    		client.streamHasClosed(path,this);
    	}
    }

    private void completeUpload() throws IOException {
    	flush();
        dtp.close();
        
        long time = System.currentTimeMillis();
        try {
        	ClientFtpResponse res = client.readResponse();
        	if( !res.isPositiveComplet()) {
        		throw new IOException("Error completing transfer.  response = "+res);
        	}
        } catch(SocketTimeoutException ex) {
        	throw new IOException("Timeout Error completing the upload time="+(System.currentTimeMillis()-time));
        } 

    }

    public void flush() throws IOException {
        out.flush();
    }

    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    public void write(byte[] b) throws IOException {
        out.write(b);
    }

}
