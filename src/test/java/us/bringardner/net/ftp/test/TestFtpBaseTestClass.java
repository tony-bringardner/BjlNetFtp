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
 * ~version~V000.01.56-V000.01.48-V000.01.39-V000.01.38-V000.01.37-V000.01.35-V000.01.34-V000.01.32-V000.01.30-V000.01.29-V000.01.18-V000.01.13-V000.01.12-V000.01.11-V000.01.10-V000.01.09-V000.01.07-V000.01.05-V000.01.04-V000.00.03-V000.00.01-V000.00.00-
 */
package us.bringardner.net.ftp.test;



import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import us.bringardner.core.BjlLogger;
import us.bringardner.core.ILogger;
import us.bringardner.core.ILogger.Level;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.net.framework.server.FileBasedAcl;
import us.bringardner.net.framework.server.IAccessControlList;
import us.bringardner.net.framework.server.IPrincipal;
import us.bringardner.net.framework.server.IServer;
import us.bringardner.net.ftp.FTP;
import us.bringardner.net.ftp.client.ClientFtpResponse;
import us.bringardner.net.ftp.client.FtpClient;
import us.bringardner.net.ftp.server.FtpRequestProcessor;
import us.bringardner.net.ftp.server.FtpServer;


@TestMethodOrder(OrderAnnotation.class)
public abstract class TestFtpBaseTestClass {

	protected static boolean implicitSecure = false;
	protected static int ftpPort2=8026;
	protected static boolean useFileBasedAcl = true; 
	protected static Level serverLogLevel = Level.ERROR;
	protected static Level clientLogLevel = Level.ERROR;
	private static FtpServer ftpServer;

	private static FileSource root;
	private static String rootPath = "target/FtpRoot";
	private static String testFileDirName = "TestFiles";

	public static FtpClient _client;

	private static IAccessControlList acl;
	protected static String user = "admin";
	protected static String password = "0000";
	private static String userRoot = null;
	private static String userDir = null;
	
	static class TrustAll implements X509TrustManager {

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			throw new CertificateException("This object is not intended for clinet processing.");					
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			//normally the trust manager will throw an exception here if it does not like the certificate.
			//  for testing just accept anything.					

		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return null;
		}

	}; 

	public static void excuteOsCommand(String filePath) throws IOException{
		boolean isWindows = System.getProperty("os.name").toLowerCase().indexOf("windows") >= 0;
		Process p = null;
		if(isWindows){
			p = Runtime.getRuntime().exec("cmd /c start " + filePath);	        
		}else {
			p = Runtime.getRuntime().exec(new String[] {filePath}, null);
			//p = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", filePath}, null);
		}
		try {
			p.waitFor(10, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		int exit = p.exitValue();
		assertEquals(0,exit,"Invalid exit code");
		String expect = "Generating 2,048 bit RSA key pair and self-signed certificate (SHA256withRSA) with a validity of 90 days\n"
				+ "	for: CN=bringardner.us, OU=AA, O=BBB, L=Bringardner, ST=CCCC, C=DD";
		String tmp = new String(p.getInputStream().readAllBytes())+new String(p.getErrorStream().readAllBytes());
		
		assertEquals(expect.trim(),tmp.trim(),"Invalid response text ");

	}

	public static  FtpClient getFtpClient() throws IOException {
		if( _client == null ) {
			
			_client = new FtpClient("localhost", ftpPort2);
			ILogger logger = _client.getLogger();
			// Put the client logging on err so I tell client log from server log at runtime.
			if (logger instanceof BjlLogger	) {
				BjlLogger l = (BjlLogger) logger;
				l.setOut(System.err);
			}
			boolean s = ftpServer.isSecure();
			_client.setSecure(s);
			_client.getLogger().setLevel(clientLogLevel);
			_client.setCmdTimeout(100000);
			_client.setCmdTimeout(9999999);
			
			_client.setTrustManagers(new TrustManager[] {
					new TrustAll()
			});
			
			

			boolean ok = _client.connect(user, password, null);
			if( !ok ) {
				_client = null;
			}
			assertTrue(ok,"FtpClient can't connect to server");

			if( useFileBasedAcl) {
				IPrincipal p = acl.getPrincipal(user);
				p.authenticate(password.getBytes());
				
				userRoot = (String)p.getParameter(FtpRequestProcessor.PARAMETER_ROOT);
				
				if( userRoot != null ) {
					FileSource file = FileSourceFactory.getDefaultFactory().createFileSource(userRoot);
					if( !file.exists() && file.isDirectory()) {
						assertTrue(false,userRoot+" (User root) does not exist. ");
					}
				}

				userDir = (String) p.getParameter(FtpRequestProcessor.PARAMETER_DEFAULT_DIRECTORY);
				if( userDir != null ) {
					String userDir1 = _client.getCurrentDir();
					assertEquals(userDir, userDir1,"User dir is wrong");
				}
			}

			if( !_client.setCurrentDir(testFileDirName)) {
				assertTrue(_client.mkDir(testFileDirName),"client can't create "+testFileDirName);
				assertTrue(_client.setCurrentDir(testFileDirName),"Can't set current dir to "+testFileDirName);			
			}

		}

		return _client;

	}

	public static void deleteAll(FileSource file) throws IOException {
		if( file.exists()) {
			if( file.isDirectory()) {
				for(FileSource kid : file.listFiles()) {
					deleteAll(kid);
				}
			}
			file.delete();
		}

	}


	public  static void startFtpServer() throws IOException  {
		System.setProperty("FtpServer.KeyStoreName", "target/serverkeystore.p12");
		System.setProperty("FtpServer.KeyStorePassword", "peekab00");
		System.setProperty("FtpServer.KeyStoreType", "PKCS12");
		System.setProperty("FtpServer.Algorithm", "SunX509");
		System.setProperty("FtpServer.Protocol", "TLSv1.3");
		System.setProperty("us.bringardner.net.ftp.client.FtpClient.Protocol", "TLSv1.3");

		File file = new File("target/serverkeystore.p12");
		if( !file.exists()) {
			excuteOsCommand("./makecert.sh");
		}
		
		// start an FTP server 
		try {

			if( useFileBasedAcl) {
				//  this is for testing the server will use a different one based on the properties. 
				System.setProperty(FileBasedAcl.PROP_FILE_NAME, "FtpTestAcl.txt");
				System.setProperty(IServer.AUTHENTICATOION_PROVIDER_PROPERTY	, FileBasedAcl.class.getCanonicalName());
			} 
			
			//  Force a FileProxy storage
			root = FileSourceFactory.getDefaultFactory().createFileSource(rootPath);
			if( !root.exists() ) {
				root.mkdirs();
			}

			
			ftpServer = new FtpServer();
			ftpServer.setSecure(implicitSecure);
			//  this creates a sand box for the users
			ftpServer.setFtpRoot(root);			
			ftpServer.setPort(ftpPort2);
			ftpServer.getLogger().setLevel(serverLogLevel);
			acl = ftpServer.getAccessControl();
			
			
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
			assertTrue(ftpServer.isRunning());
			//System.out.println("Ftp server running on port "+ftpPort);

		} catch(Throwable e) {
			System.out.println("Can't start the FTP Server error = "+e);
		}

	}

	public static  void stopFtpServer() throws IOException {

		if( _client != null && _client.isConnected()) {
			/*
			 * delete test directory on server
			 * testFileDirName is relative from the root
			 */
			_client.setCurrentDir("/");


			// cleanup test files
			deleteAll(root);

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
	@Order(4)
	public void testOptsAndList() throws IOException, ParseException {
		//supported={REST=REST STREAM, MDTM=MDTM, MLST=MLST MODIFY*;PERM*;SIZE*;TYPE*;Unix.group*;Unix.owner*;, SIZE=SIZE, TVFS=TVFS}

		String [] expect = {"REST", "MDTM", "MLST","SIZE", "TVFS"};

		FtpClient client = getFtpClient();
		Map<String, String> supported = client.getFeatResponse();
		assertEquals(expect.length, supported.size(),"Supported facts are wrong");
		for(String name : expect) {
			assertTrue(supported.containsKey(name),"fact "+name+" is missing");
		}

		ClientFtpResponse resp = client.executeCommand(FTP.OPTS+" mlst size;type;modify;");
		assertEquals(200,resp._getResponseCode(), FTP.OPTS+" command wrong status");

		String dir = client.getCurrentDir();
		resp = client.executeCommand(FTP.MLST,dir);

		assertEquals(250,resp._getResponseCode(), FTP.MLST+" "+dir+" command wrong status");

		// test the values 
		//  create a file
		Date now = new Date();
		String remoteFileName = "OptsTest.txt";
		
		try(OutputStream out = client.getOutputStream(remoteFileName)) {
			out.write(APPE_TEXT.getBytes());		
		}
		
		Calendar cal = Calendar.getInstance();
		String [] ls = client.executeList(remoteFileName);
		assertEquals(1, ls.length,"Should be only one file");
		//MODIFY=20241214101035.000;PERM=adfwr;SIZE=1330;TYPE=file;Unix.group=xxx;Unix.owner=xxx; /OpptsTest.txt
		String parts[] = ls[0].trim().split("[;]");

		SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMddHHmmss.SSS");


		for(String part : parts) {
			String [] pair = part.split("[=]");
			if( pair.length==1) {
				assertEquals(remoteFileName, part.trim(),"Should be the file name");
			} else {
				assertEquals(2, pair.length,"Should key/value pair. part="+part);
				if( pair[0].equals("MODIFY")) {
					Date date = fmt.parse(pair[1]);
					long delta = Math.abs(now.getTime()-date.getTime());
					//  There will be a difference.  How large  depends on the system and garbage collection :-)
					assertTrue(delta < 1000,"Modified date should be now.");		
				} else if( pair[0].equals("PERM")) {
					assertEquals("adfwr", pair[1].trim(),"Permission are not correct");
				}else if( pair[0].equals("SIZE")) {
					assertEquals(""+APPE_TEXT.length(), pair[1].trim(),"Size is not correct");
				}else if( pair[0].equals("TYPE")) {
					assertEquals("file", pair[1].trim(),"Should be a file");
				}else if( pair[0].equals("Unix.group")) {
					//for testing, I'm just going to assume the group is correct.
				}else if( pair[0].equals("Unix.owner")) {
					assertEquals(System.getProperty("user.name"), pair[1].trim(),"Should be the file name");
				} else {
					assertTrue(false,"Un expected fact="+part);
				}
			}
		}

		ls = client.executeList(true, remoteFileName);
		assertEquals(1, ls.length,"Should only be one entry");

		//-rw-r--r--   1 user  group                       1330 Dec 14 12:25 OptsTest.txt
		parts = ls[0].split("\\s+");
		assertEquals(9, parts.length,"Should only be 9 parts. val="+ls[0]);

		int pos = 0;
		assertEquals("-rw-r--r--", parts[pos],"Permission don't match"+parts[pos]);
		assertEquals("1", parts[++pos],"Link count does not match"+parts[pos]);
		assertEquals(System.getProperty("user.name"), parts[++pos],"User name does not match"+parts[pos]);
		assertEquals(parts[++pos], parts[pos],"Assume the group is correct");
		assertEquals(""+APPE_TEXT.length(), parts[++pos],"Size does not match"+parts[pos]);


		String tmp = cal.getDisplayName(Calendar.MONTH, Calendar.SHORT_FORMAT, Locale.getDefault());
		assertEquals(tmp, parts[++pos],"Month does not match"+parts[pos]);

		assertEquals(""+cal.get(Calendar.DAY_OF_MONTH), parts[++pos],"Day does not match"+parts[pos]);
		String val = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE));

		assertEquals(val, parts[++pos].trim(),"Hour / Minute does not match part="+parts[pos]);
		assertEquals(remoteFileName, parts[++pos],"Filename does not match"+parts[pos]);

		//  delete the file
		assertTrue(client.delete(remoteFileName),"Can't delete "+remoteFileName);

	}

	/**

	  	RFC 959 October 1985
	   	CHANGE TO PARENT DIRECTORY (CDUP)

            This command is a special case of CWD, and is included to
            simplify the implementation of programs for transferring
            directory trees between operating systems having different
            syntaxes for naming the parent directory.  

            The reply codes shall be identical to the reply codes of CWD.  
            See Appendix II for further details.

	 *
	 * @throws IOException
	 */
	@Test
	@Order(3)
	public void testCdup() throws IOException {
		FtpClient client = getFtpClient();
		String dir = client.getCurrentDir();
		String remoteFileName = dir+"/CupdDir";
		if( !client.setCurrentDir(remoteFileName)) {
			assertTrue(_client.mkDir(remoteFileName),"client can't create "+remoteFileName);
			assertTrue(_client.setCurrentDir(remoteFileName),"Can't set current dir to "+remoteFileName);			
		}
		client.executeCdup();
		String dir2 = client.getCurrentDir();
		assertEquals(dir, dir2,"Cdup did not work as expected");

		assertTrue(client.delete(remoteFileName),"Can't delete "+remoteFileName);



	}

	public static final String APPE_TEXT = ""
			+ "RFC 959\n"
			+ "	 * \n"
			+ "	 * APPEND (with create) (APPE)\n"
			+ "\n"
			+ "            This command causes the server-DTP to accept the data\n"
			+ "            transferred via the data connection and to store the data in\n"
			+ "            a file at the server site.  If the file specified in the\n"
			+ "            pathname exists at the server site, then the data shall be\n"
			+ "            appended to that file; otherwise the file specified in the\n"
			+ "            pathname shall be created at the server site.\n"
			+ "\n"
			+ "\n"
			+ "			Implementation is the same as STOR except the output stream is \n"
			+ "			set for append.\n"
			+ "";			
	/**
	 * @throws IOException
	 */
	@Test
	@Order(2)
	public void testAppe() throws IOException {
		FtpClient client = getFtpClient();
		String remoteFileName = "AborText.txt";
		byte [] data = APPE_TEXT.getBytes();

		//  first create the file and put some data in it
		try(OutputStream out = client.getOutputStream(remoteFileName)) {
			out.write(data);			
		}
		long sz = client.executeSize(remoteFileName);
		assertEquals(data.length, (int)sz,"File size after write is not correct");
		try(OutputStream out = client.getOutputStream(remoteFileName,true,true)) {
			out.write(data);			
		}
		long sz2 = client.executeSize(remoteFileName);
		assertEquals(data.length*2, (int)sz2,"File size after append is not correct");

		//  delete the file
		assertTrue(client.delete(remoteFileName),"Can't delete "+remoteFileName);

	}
	

	@Test
	@Order(1)
	public void testSendFileWithClient () throws UnknownHostException, IOException {

		FileSource localDir = FileSourceFactory.getDefaultFactory().createFileSource(testFileDirName);
		FtpClient client = getFtpClient();


		// write all test files to the server
		FileSource[] localKids = localDir.listFiles();
		for(FileSource file : localKids) {
			if( file.isDirectory()) {
				throw new IOException("Not expected");
			} else {
				InputStream in = file.getInputStream();
				OutputStream out = client.getOutputStream(file.getName());				
				copy(in, out);
			}
		}

		/**
		 * Validate that the files are in the right place based on
		 * user root and defaultDir
		 */
		
		
		//  Read all lest files from server and compare to local
		for(FileSource file : localKids) {
			if( file.isDirectory()) {
				throw new IOException("Not expected");
			} else {
				InputStream in1 = file.getInputStream();
				InputStream in2 = client.getInputStream(file.getName());
				compare(file.getName(), in1, in2);
			}
		}

		// delete test files from server
		for(FileSource file : localKids) {
			if( file.isDirectory()) {
				throw new IOException("Not expected");
			} else {
				assertTrue( client.delete(file.getName()),"Can't delete "+file.getName()+" from server");
			}
		}
	}

	/**
	 * Compare the bytes of two input streams
	 * 
	 * @param in1
	 * @param in2
	 * @throws IOException
	 */
	private void compare(String name,InputStream in1, InputStream in2) throws IOException {

		// use a small buffer to get multiple reads 
		BufferedInputStream bin1 = new BufferedInputStream(in1);
		BufferedInputStream bin2 = new BufferedInputStream(in2);

		try {
			int ch = bin1.read();
			int pos = 0;
			while( ch > 0) {
				assertEquals(ch, bin2.read(),name+" compare pos="+pos);
				pos++;
				ch = bin1.read();				
			}
			assertEquals(ch, bin2.read(),name+" compare pos="+pos);
		} finally {
			try {
				in1.close();
			} catch (Exception e) {
			}
			try {
				in2.close();
			} catch (Exception e) {
			}

		}

	}

	private void copy(InputStream in, OutputStream out) throws IOException {
		// use a small buffer to get multiple reads 
		byte [] data = new byte[1024];
		int got = 0;

		try {
			while( (got=in.read(data)) >= 0) {
				if( got > 0 ) {
					out.write(data,0,got);
				}
			}

		} finally {
			try {
				out.close();
			} catch (Exception e) {
			}
			try {
				in.close();
			} catch (Exception e) {
			}

		}
	}



}
