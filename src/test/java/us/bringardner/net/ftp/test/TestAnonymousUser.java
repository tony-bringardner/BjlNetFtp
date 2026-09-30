package us.bringardner.net.ftp.test;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import us.bringardner.net.framework.server.IServer;

public class TestAnonymousUser extends TestFtpBaseTestClass {

	@BeforeAll
	public  static void startFtpServer() throws IOException  {
		TestFtpBaseTestClass.useFileBasedAcl=false;
		// Other test classes configure a file based ACL through system properties; clear it so
		// this class gets the built in anonymous ACL regardless of test execution order.
		System.clearProperty(IServer.AUTHENTICATOION_PROVIDER_PROPERTY);
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
