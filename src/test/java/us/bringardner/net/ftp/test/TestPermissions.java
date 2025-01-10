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
 * ~version~V000.01.57-V000.01.56-V000.01.48-V000.01.39-V000.01.38-V000.01.37-V000.01.35-V000.01.34-V000.01.32-V000.01.30-V000.01.29-V000.01.18-V000.01.13-V000.01.12-V000.01.11-V000.01.10-V000.01.09-V000.01.07-V000.01.05-V000.01.04-V000.00.03-V000.00.01-V000.00.00-
 */
package us.bringardner.net.ftp.test;



import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import junit.framework.TestCase;
import us.bringardner.core.BjlLogger;
import us.bringardner.core.ILogger;
import us.bringardner.core.ILogger.Level;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.net.framework.server.FileBasedAcl;
import us.bringardner.net.framework.server.IServer;
import us.bringardner.net.ftp.client.FtpClient;
import us.bringardner.net.ftp.client.FtpClient.Permissions;
import us.bringardner.net.ftp.server.FtpServer;


@TestMethodOrder(OrderAnnotation.class)
public class TestPermissions {

	protected static int ftpPort2=8030;
	protected static Level serverLogLevel = Level.ERROR;
	protected static Level clientLogLevel = Level.ERROR;
	private static FtpServer ftpServer;

	private static FileSource root;
	private static String rootPath = "target/FtpRoot2";
	private static String testFileDirName = "TestFiles";

	public static FtpClient _client;

	protected static String user = "admin";
	protected static String password = "0000";
	
	
	public static  FtpClient getFtpClient() throws IOException {
		if( _client == null ) {
			
			_client = new FtpClient("localhost", ftpPort2);
			ILogger logger = _client.getLogger();
			// Put the client logging on err so I tell client log from server log at runtime.
			if (logger instanceof BjlLogger	) {
				BjlLogger l = (BjlLogger) logger;
				l.setOut(System.err);				
			}
			_client.setSecure(false);
			_client.setRequestSecure(false);
			_client.getLogger().setLevel(clientLogLevel);
			_client.setCmdTimeout(100000);
			_client.setCmdTimeout(9999999);
			
			boolean ok = _client.connect(user, password, null);
			if( !ok ) {
				_client = null;
			}
			assertTrue(ok,"FtpClient can't connect to server");

			if( !_client.setCurrentDir(testFileDirName)) {
				assertTrue(_client.mkDir(testFileDirName),"client can't create "+testFileDirName);
				assertTrue(_client.setCurrentDir(testFileDirName),"Can't set current dir to "+testFileDirName);			
			}

		}

		return _client;

	}


	@BeforeAll
	public  static void startFtpServer() throws IOException  {
		// start an FTP server 
		try {

			//  Force a FileProxy storage
			root = FileSourceFactory.getDefaultFactory().createFileSource(rootPath);
			if( !root.exists() ) {
				root.mkdirs();
			}

			System.setProperty(FileBasedAcl.PROP_FILE_NAME, "FtpTestAcl.txt");
			System.setProperty(IServer.AUTHENTICATOION_PROVIDER_PROPERTY	, FileBasedAcl.class.getCanonicalName());
			
			ftpServer = new FtpServer();
			ftpServer.setSecure(false);
			
			//  this creates a sand box for the users
			ftpServer.setFtpRoot(root);			
			ftpServer.setPort(ftpPort2);
			ftpServer.getLogger().setLevel(serverLogLevel);
			
			ftpServer.start();
			int cnt = 0;
			// wait for ftpServer to start
			while(cnt < 5 && !ftpServer.isRunning()) {
				cnt++;
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
			}
			TestCase.assertTrue(ftpServer.isRunning());
			//System.out.println("Ftp server running on port "+ftpPort);

		} catch(Throwable e) {
			System.out.println("Can't start the FTP Server error = "+e);
		}

	}

	@AfterAll
	public static  void stopFtpServer() throws IOException {

		if( _client != null && _client.isConnected()) {
			/*
			 * delete test directory on server
			 * testFileDirName is relative from the root
			 */
			_client.setCurrentDir("/");


			// cleanup test files
			TestFtpBaseTestClass.deleteAll(root);

			_client.close();

		}

		_client = null;

		if( root != null ) {
			try {
				root.delete();
			} catch (Exception e) {
			}
		}

		if( ftpServer != null ) {
			try {
				ftpServer.stop();
				long start = System.currentTimeMillis();
				while(ftpServer.isRunning() && (System.currentTimeMillis()-start)< 10000) {
					Thread.sleep(10);
				}
				assertFalse(ftpServer.isRunning(),"Serve did not stop.");
			} catch (Exception e) {
			}
		}
	}

	@Test
	@Order(1)
	public void testPermissions() throws IOException {

		FtpClient client = getFtpClient();
		String remoteFile = "PermissionTest.txt";
		
		try(OutputStream out = client.getOutputStream(remoteFile)) {
			out.write("Put some data in the file".getBytes());
		}
		

		for(FtpClient.Permissions p : FtpClient.Permissions.values()) {
			//  if we turn off owner write we won't be able to turn it back on.
			if( p != Permissions.OwnerWrite) {
				changeAndValidatePermission(p,remoteFile);
			}
		}
		
		assertTrue(client.delete(remoteFile),"Can't delete "+remoteFile);
				
	}

	private boolean setPermission(FtpClient.Permissions p, String path,boolean b) throws IOException {
		boolean ret = false;
		switch (p) {
		case OwnerRead: 	ret = getFtpClient().setOwnerReadable(path,b); break;
		case OwnerWrite:	ret = getFtpClient().setOwnerWritable(path,b); break;
		case OwnerExecute:	ret = getFtpClient().setOwnerExecutable(path,b); break;

		case GroupRead: 	ret = getFtpClient().setGroupReadable(path,b); break;
		case GroupWrite:	ret = getFtpClient().setGroupWritable(path,b); break;
		case GroupExecute:	ret = getFtpClient().setGroupExecutable(path,b); break;

		case OtherRead: 	ret = getFtpClient().setOtherReadable(path,b); break;
		case OtherWrite:	ret = getFtpClient().setOtherWritable(path,b); break;
		case OtherExecute:	ret = getFtpClient().setOtherExecutable(path,b); break;

		default:
			throw new RuntimeException("Invalid permision="+p);
		}
		
		return ret;
	}
	
	private boolean getPermission(Permissions p, String path) throws IOException {
		boolean ret = false;
		switch (p) {
		case OwnerRead:    ret = getFtpClient().canOwnerRead(path); break;
		case OwnerWrite:   ret = getFtpClient().canOwnerWrite(path); break;
		case OwnerExecute: ret = getFtpClient().canOwnerExecute(path); break;
		
		case GroupRead:    ret = getFtpClient().canGroupRead(path); break;
		case GroupWrite:   ret = getFtpClient().canGroupWrite(path); break;
		case GroupExecute: ret = getFtpClient().canGroupExecute(path); break;
		
		case OtherRead:    ret = getFtpClient().canOtherRead(path); break;
		case OtherWrite:   ret = getFtpClient().canOtherWrite(path); break;
		case OtherExecute: ret = getFtpClient().canOtherExecute(path); break;
		
		default:
			throw new RuntimeException("Invalid permision="+p);			
		}
		return ret;
	}
	
	private void changeAndValidatePermission(Permissions p, String file) throws IOException {
		
		//Get the current value		
		boolean b = getPermission(p, file);
		
		// toggle it 
		assertTrue(setPermission(p, file, !b),"set permission failed p="+p);
		boolean b2 = getPermission(p, file);
		assertEquals(b2, !b,"permision did not change p="+p);
		
		// Set it back
		assertTrue(setPermission(p, file, b),"reset permission failed p="+p);		
		assertEquals(getPermission(p, file), b,"permision did not change back to original p="+p);
		
		
	}




}
