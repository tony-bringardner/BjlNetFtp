package us.bringardner.net.ftp.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/** BJL-7: the test keystore is made, reused and replaced without depending on keytool's messages. */
public class TestKeystoreHelper {

	@Test
	public void aKeystoreIsMadeAndReused() throws Exception {
		File file = new File("target/keystore-helper-test.p12");
		Files.deleteIfExists(file.toPath());
		TestFtpBaseTestClass.makeTestKeystore(file);
		assertTrue(TestFtpBaseTestClass.isUsableKeystore(file));

		long made = file.lastModified();
		Thread.sleep(1100);
		TestFtpBaseTestClass.makeTestKeystore(file);
		assertEquals(made, file.lastModified(), "a usable keystore should be kept");
		Files.deleteIfExists(file.toPath());
	}

	@Test
	public void anExpiredOrBrokenKeystoreIsReplaced() throws Exception {
		File file = new File("target/keystore-helper-expired.p12");
		Files.deleteIfExists(file.toPath());
		// a certificate that expired yesterday
		String keytool = Paths.get(System.getProperty("java.home"), "bin",
				System.getProperty("os.name").toLowerCase().contains("windows") ? "keytool.exe" : "keytool").toString();
		Process p = new ProcessBuilder(keytool, "-genkeypair", "-noprompt", "-alias", TestFtpBaseTestClass.TEST_KEY_ALIAS,
				"-dname", "CN=expired", "-keystore", file.getPath(), "-storetype", "PKCS12",
				"-storepass", TestFtpBaseTestClass.TEST_KEYSTORE_PASSWORD, "-keypass", TestFtpBaseTestClass.TEST_KEYSTORE_PASSWORD,
				"-keyalg", "RSA", "-keysize", "2048", "-startdate", "-3d", "-validity", "1")
				.redirectErrorStream(true).redirectOutput(new File("target/keystore-helper-expired.log")).start();
		assertEquals(0, p.waitFor());
		assertTrue(file.isFile());
		assertFalse(TestFtpBaseTestClass.isUsableKeystore(file), "expired");
		TestFtpBaseTestClass.makeTestKeystore(file);
		assertTrue(TestFtpBaseTestClass.isUsableKeystore(file));

		Files.write(file.toPath(), new byte[] {1, 2, 3});
		assertFalse(TestFtpBaseTestClass.isUsableKeystore(file), "broken");
		TestFtpBaseTestClass.makeTestKeystore(file);
		assertTrue(TestFtpBaseTestClass.isUsableKeystore(file));
		Files.deleteIfExists(file.toPath());
	}
}
