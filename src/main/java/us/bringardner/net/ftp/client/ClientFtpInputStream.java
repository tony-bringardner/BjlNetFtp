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
 * ~version~V000.01.51-V000.01.47-V000.01.03-V000.01.02-V000.00.01-V000.00.00-
 */
/*
 * Created on Dec 14, 2006
 *
 */
package us.bringardner.net.ftp.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;

import us.bringardner.net.ftp.FTP;

public class ClientFtpInputStream extends InputStream implements FTP {

    //  This client is a private connection just for downloading one file.
    private FtpClient client;
    private String path;
    private InputStream in ;
    private boolean eof = false;
    private ClientDataTransferProcess dtp ;
    private boolean ascii;
    private long startAt;

    public ClientFtpInputStream(String path, FtpClient client) throws IOException {
        this(path,client,false);
    }

    public ClientFtpInputStream(String path, FtpClient client, boolean ascii) throws IOException {
        this(path,client,false,0l);
    }

    public ClientFtpInputStream(String path, FtpClient client, boolean ascii, long startingPos) throws IOException {
        this.path = path;
        this.ascii = ascii;
        this.client = client;
        this.startAt = startingPos;
        startDownload();

    }

    
    private void startDownload() throws IOException {
        if( ascii ) {
            client.setAsciiType();
        } else {
            client.setImageType();
        }
        boolean canRestore = false;
        
        if( startAt > 0l) {
            canRestore = client.getFeatResponse().containsKey(REST);
            if(canRestore) {
                //  WE can do a restore
                ClientFtpResponse res = client.executeCommand(REST,""+startAt);
                if( !res.isPositiveIntermediate()) {
                    canRestore = false;
                    logError(" Sever said it would support REST but did not.  ResponseCode="+res._getResponseCode());
                }

            }
        }

        
        dtp = client.getDataTransferProcess();
        in = dtp.getInput();
        ClientFtpResponse res = client.executeCommand(RETR, path.trim());
        
        if(!res.isPositivePreliminay()) {
            throw new IOException ("Error invalid respones to RETR = "+res._getResponseCode());
        }
        
        if( startAt > 0l && !canRestore) {
            //  Server could not do it so ignore the startAt data
            long skipped = in.skip(startAt);
            if( skipped != startAt ) {
                throw new IOException("Server would not honor REST and we could not skip "+startAt+" bytes of data");
            }
        }
    }

    private void logError(String msg) {
        client.logError(getClass().getName()+" : "+msg);   
    }

    private void completeDownload() throws IOException {
        dtp.close();
        
        
        try {
            ClientFtpResponse res = client.readResponse();
            if( !res.isPositiveComplet()) {
                throw new IOException("Error completing transfer.  response = "+res);
            }

        } catch(SocketTimeoutException ex) {
            try {
                String tmp = client.executePwd();
                System.out.println("tmp = "+tmp);
            } catch(Exception e) {
                System.out.println("Error "+e);
                e.printStackTrace();
            }
        } 
        
    }

    public int available() throws IOException {
        return in.available();
    }

    public int read(byte[] arg0) throws IOException {
        int ret = -1;
        if( !eof ) {
            if( (ret = in.read(arg0)) == -1) {
                eof = true;
            }
        }
        
        return ret;
    }

    public long skip(long arg0) throws IOException {
        return super.skip(arg0);
    }

    public int read() throws IOException {
        int ret = -1;
        if( !eof ) {
            if((ret=in.read()) == -1 ) {
                eof = true; 
            }
        }
        return ret;
    }


    public int read(byte[] b, int off, int len) throws IOException {
        int ret = in.read(b, off, len);
        if( ret == -1 ) {
            eof = true;
        }
        return ret;
    }


    public void close() throws IOException {
        super.close();
        try {
        	completeDownload();	
		} catch (Exception e) {
			client.logDebug(getClass().getName()+" : Error closing "+e);   
		}
        
        client.streamHasClosed(path, this);
    }




}
