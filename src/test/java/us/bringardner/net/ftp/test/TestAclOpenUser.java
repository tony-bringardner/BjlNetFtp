package us.bringardner.net.ftp.test;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class TestAclOpenUser extends TestFtpBaseTestClass {

	@BeforeAll
	public  static void startFtpServer() throws IOException  {
		TestFtpBaseTestClass.useFileBasedAcl=true;
		TestFtpBaseTestClass._client = null;
		TestFtpBaseTestClass.ftpPort2 = 8025;
		TestFtpBaseTestClass.user = "open";
		TestFtpBaseTestClass.password = "0000";
		TestFtpBaseTestClass.implicitSecure = false;
		TestFtpBaseTestClass.startFtpServer();
		
	}
	
	@AfterAll
	public static  void stopFtpServer() throws IOException {
		TestFtpBaseTestClass.stopFtpServer();
		
	}

}
