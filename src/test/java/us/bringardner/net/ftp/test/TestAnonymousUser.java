package us.bringardner.net.ftp.test;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class TestAnonymousUser extends TestFtpBaseTestClass {

	@BeforeAll
	public  static void startFtpServer() throws IOException  {
		TestFtpBaseTestClass.useFileBasedAcl=false;
		TestFtpBaseTestClass.implicitSecure = false;
		
		TestFtpBaseTestClass._client = null;
		TestFtpBaseTestClass.ftpPort2 = 8022;
		TestFtpBaseTestClass.user = "ftp";
		TestFtpBaseTestClass.password = "ftp";
		TestFtpBaseTestClass.startFtpServer();
		
	}
	
	@AfterAll
	public static  void stopFtpServer() throws IOException {
		TestFtpBaseTestClass.stopFtpServer();
		
	}

}
