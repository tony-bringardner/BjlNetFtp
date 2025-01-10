package us.bringardner.net.ftp.test;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class TestAclLockedUser extends TestFtpBaseTestClass {

	@BeforeAll
	public  static void startFtpServer() throws IOException  {
		TestFtpBaseTestClass.useFileBasedAcl=true;
		TestFtpBaseTestClass.implicitSecure = false;
		TestFtpBaseTestClass.ftpPort2 = 8023;
		TestFtpBaseTestClass.user = "locked";
		TestFtpBaseTestClass._client = null;
		TestFtpBaseTestClass.password = "0000";
		TestFtpBaseTestClass.startFtpServer();
		
	}
	
	@AfterAll
	public static  void stopFtpServer() throws IOException {
		TestFtpBaseTestClass.stopFtpServer();
		
	}

}
