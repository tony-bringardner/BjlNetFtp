package us.bringardner.net.ftp.test;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class TestAdminUser extends TestFtpBaseTestClass {

	@BeforeAll
	public  static void startFtpServer() throws IOException  {
		TestFtpBaseTestClass._client = null;
		TestFtpBaseTestClass.ftpPort2 = 8024;
		TestFtpBaseTestClass.user = "admin";
		TestFtpBaseTestClass.password = "0000";
		TestFtpBaseTestClass.useFileBasedAcl=true;
		TestFtpBaseTestClass.implicitSecure = false;
		TestFtpBaseTestClass.startFtpServer();
		
	}
	
	@AfterAll
	public static  void stopFtpServer() throws IOException {
		TestFtpBaseTestClass.stopFtpServer();
		
	}

}
