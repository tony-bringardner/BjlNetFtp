package us.bringardner.net.ftp.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.core.ILogger.Level;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.net.framework.server.FileBasedAcl;
import us.bringardner.net.framework.server.IServer;
import us.bringardner.net.ftp.client.FtpClient;
import us.bringardner.net.ftp.server.FtpServer;
import us.bringardner.net.ftp.test.TestTransferReliability.Reply;
import us.bringardner.net.ftp.test.TestTransferReliability.Session;

/**
 * Security regression tests:
 * <ul>
 * <li>No path (.., absolute, symbolic link, sibling directory with the same prefix)
 *     can reach outside the user's root.</li>
 * <li>RFC 2577: PORT/EPRT can't target another host or a privileged port, and a
 *     passive data connection from another host is refused.</li>
 * <li>Passwords are not kept in the client's dialog, and ACCT can't reuse a password.</li>
 * </ul>
 */
public class TestSecurity {

	private static final int PORT = 8032;
	private static final String BASE = "target/FtpSecurity";

	private static FtpServer server;
	private static File base, root, outside, sibling;

	@BeforeAll
	public static void startServer() throws Exception {
		System.clearProperty(IServer.AUTHENTICATOION_PROVIDER_PROPERTY);
		System.clearProperty(FileBasedAcl.PROP_FILE_NAME);

		base = new File(BASE).getAbsoluteFile();
		deleteAll(base);
		root = new File(base, "root");
		outside = new File(base, "outside");
		sibling = new File(base, "rootX"); // same prefix as root
		assertTrue(root.mkdirs() && outside.mkdirs() && sibling.mkdirs());
		Files.write(new File(outside, "secret.txt").toPath(), "secret".getBytes(StandardCharsets.UTF_8));
		Files.write(new File(sibling, "secret.txt").toPath(), "sibling".getBytes(StandardCharsets.UTF_8));
		Files.write(new File(root, "inside.txt").toPath(), "inside".getBytes(StandardCharsets.UTF_8));
		new File(root, "sub").mkdirs();

		FileSource fs = FileSourceFactory.getDefaultFactory().createFileSource(root.getAbsolutePath());
		server = new FtpServer();
		server.setSecure(false);
		server.setFtpRoot(fs);
		server.setPort(PORT);
		server.setDataTimeout(2000);
		server.getLogger().setLevel(Level.ERROR);
		server.start();
		long start = System.currentTimeMillis();
		while (!server.isRunning() && System.currentTimeMillis() - start < 5000) {
			Thread.sleep(20);
		}
		assertTrue(server.isRunning(), "server did not start");
	}

	@AfterAll
	public static void stopServer() throws Exception {
		if (server != null) {
			server.stop();
			long start = System.currentTimeMillis();
			while (server.isRunning() && System.currentTimeMillis() - start < 10000) {
				Thread.sleep(10);
			}
		}
		deleteAll(base);
	}

	// ------------------------------------------------------------------ path traversal

	@Test
	public void fileInsideRootIsVisible() throws Exception {
		try (Session s = new Session(PORT)) {
			s.send("SIZE inside.txt");
			assertEquals("6", s.expect(213).text);
		}
	}

	@Test
	public void dotDotCannotReachOutsideRoot() throws Exception {
		String[] paths = { "../outside/secret.txt", "/../outside/secret.txt", "sub/../../outside/secret.txt",
				"../../../../../../../../" + outside.getAbsolutePath().substring(1) + "/secret.txt",
				"..\\outside\\secret.txt", "../rootX/secret.txt", outside.getAbsolutePath() + "/secret.txt" };
		try (Session s = new Session(PORT)) {
			for (String p : paths) {
				s.send("SIZE " + p);
				Reply r = s.read(5000);
				assertEquals(550, r.code, "SIZE " + p + " must not see a file outside the root: " + r);
			}
		}
	}

	@Test
	public void cwdCannotLeaveRoot() throws Exception {
		try (Session s = new Session(PORT)) {
			for (String dir : new String[] { "..", "../..", "/..", "sub/../..", "../outside" }) {
				s.send("CWD " + dir);
				s.read(5000); // 250 (clamped to root) or 450 (no such dir)
				s.send("PWD");
				String pwd = s.expect(257).text;
				assertTrue(pwd.contains("\"/\""), "CWD " + dir + " left the root: " + pwd);
			}
			s.send("CDUP");
			s.read(5000);
			s.send("PWD");
			assertTrue(s.expect(257).text.contains("\"/\""), "CDUP left the root");
		}
	}

	@Test
	public void cannotWriteOrDeleteOutsideRoot() throws Exception {
		try (Session s = new Session(PORT)) {
			s.send("DELE ../outside/secret.txt");
			assertTrue(s.read(5000).code >= 400);
			assertTrue(new File(outside, "secret.txt").exists(), "DELE escaped the root");

			s.send("MKD ../outside/newdir");
			s.read(5000);
			assertFalse(new File(outside, "newdir").exists(), "MKD escaped the root");

			try (Socket data = s.pasv()) {
				s.send("STOR ../outside/evil.txt");
				Reply r = s.read(5000);
				if (r.code < 200) {
					data.getOutputStream().write("evil".getBytes(StandardCharsets.UTF_8));
					data.close();
					s.read(5000);
				}
			}
			assertFalse(new File(outside, "evil.txt").exists(), "STOR escaped the root");
		}
	}

	@Test
	public void symlinkOutOfRootIsRejected() throws Exception {
		File link = new File(root, "link-out");
		File siblingLink = new File(root, "link-sibling");
		try {
			Files.createSymbolicLink(link.toPath(), outside.toPath());
			Files.createSymbolicLink(siblingLink.toPath(), sibling.toPath());
		} catch (UnsupportedOperationException | IOException e) {
			Assumptions.assumeTrue(false, "symbolic links not supported: " + e);
		}
		try (Session s = new Session(PORT)) {
			s.send("SIZE link-out/secret.txt");
			assertEquals(550, s.read(5000).code, "symlink escaped the root");
			s.send("SIZE link-sibling/secret.txt");
			assertEquals(550, s.read(5000).code, "symlink to a same-prefix sibling escaped the root");
			s.send("CWD link-out");
			assertTrue(s.read(5000).code >= 400, "CWD through symlink escaped the root");
		} finally {
			link.delete();
			siblingLink.delete();
		}
	}

	// ------------------------------------------------------------------ RFC 2577

	@Test
	public void portCannotTargetAnotherHostOrPrivilegedPort() throws Exception {
		try (Session s = new Session(PORT)) {
			for (String cmd : new String[] { "PORT 10,1,2,3,40,1", "PORT 127,0,0,1,0,25", "EPRT |1|10.1.2.3|4000|",
					"EPRT |1|127.0.0.1|25|" }) {
				s.send(cmd);
				assertEquals(501, s.read(5000).code, cmd + " should be refused");
			}
		}
	}

	@Test
	public void passiveConnectionFromAnotherHostIsRefused() throws Exception {
		// Needs a second loopback address (standard on Linux, not on macOS)
		InetAddress rogueAddr = InetAddress.getByName("127.0.0.2");
		Socket rogue = new Socket();
		try {
			rogue.bind(new InetSocketAddress(rogueAddr, 0));
		} catch (IOException e) {
			rogue.close();
			Assumptions.assumeTrue(false, "127.0.0.2 not available: " + e);
		}
		try (Session s = new Session(PORT)) {
			int port = s.pasvPort();
			rogue.connect(new InetSocketAddress("127.0.0.1", port), 2000);
			rogue.setSoTimeout(5000);
			s.send("RETR inside.txt");
			// The server must drop the rogue connection without sending it any data
			assertEquals(-1, rogue.getInputStream().read(), "rogue connection received data");

			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			try (Socket data = new Socket()) {
				data.connect(new InetSocketAddress("127.0.0.1", port), 2000);
				data.setSoTimeout(5000);
				s.expectPreliminary();
				data.getInputStream().transferTo(buf);
			}
			s.expectComplete();
			assertArrayEquals("inside".getBytes(StandardCharsets.UTF_8), buf.toByteArray());
		} finally {
			rogue.close();
		}
	}

	// ------------------------------------------------------------------ credentials

	@Test
	public void acctWithoutPassIsRefused() throws Exception {
		try (Session s = new Session(PORT)) {
			s.send("ACCT whatever");
			assertEquals(503, s.read(5000).code);
		}
	}

	@Test
	public void clientDialogDoesNotContainPassword() throws Exception {
		FtpClient client = new FtpClient("localhost", PORT);
		client.getLogger().setLevel(Level.ERROR);
		client.setRequestSecure(false); // this test server has no TLS keystore
		try {
			assertTrue(client.connect("anonymous", "SuperSecret-123", null), "can't connect");
			client.executePwd();
			String dialog = client.dialog.toString();
			assertTrue(dialog.contains("PASS ****"), "PASS should be masked: " + dialog);
			assertFalse(dialog.contains("SuperSecret-123"), "password leaked into the client dialog");
		} finally {
			client.close();
		}
	}

	// ------------------------------------------------------------------ AUTH / login

	@Test
	public void authMechanismIsCaseInsensitive() throws Exception {
		File keystore = new File("target/serverkeystore.p12");
		TestFtpBaseTestClass.makeTestKeystore(keystore);
		System.setProperty("FtpServer.KeyStoreName", keystore.getPath());
		System.setProperty("FtpServer.KeyStorePassword", "peekab00");
		System.setProperty("FtpServer.KeyStoreType", "PKCS12");
		System.setProperty("FtpServer.Algorithm", "SunX509");

		try (Session s = new Session(PORT)) {
			s.send("AUTH bogus");
			assertEquals(504, s.read(5000).code);

			s.send("AUTH tls"); // lower case: was rejected with 504
			assertEquals(234, s.read(5000).code);

			// Complete the handshake and check the control channel works over TLS
			javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
			ctx.init(null, new javax.net.ssl.TrustManager[] { new TestFtpBaseTestClass.TrustAll() }, null);
			try (javax.net.ssl.SSLSocket tls = (javax.net.ssl.SSLSocket) ctx.getSocketFactory()
					.createSocket(s.socket(), "localhost", PORT, false)) {
				tls.setUseClientMode(true);
				tls.startHandshake();
				java.io.Writer w = new java.io.OutputStreamWriter(tls.getOutputStream(), StandardCharsets.UTF_8);
				java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(tls.getInputStream(), StandardCharsets.UTF_8));
				w.write("NOOP\r\n");
				w.flush();
				String reply = r.readLine();
				assertTrue(reply != null && reply.startsWith("200"), "NOOP over TLS: " + reply);
			}
		}
	}

	@Test
	public void failedLoginIsDelayed() throws Exception {
		int old = server.getLoginFailureDelay();
		server.setLoginFailureDelay(400);
		try (Socket control = new Socket()) {
			control.connect(new InetSocketAddress("127.0.0.1", PORT), 5000);
			control.setSoTimeout(5000);
			java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(control.getInputStream(), StandardCharsets.UTF_8));
			java.io.Writer out = new java.io.OutputStreamWriter(control.getOutputStream(), StandardCharsets.UTF_8);
			assertTrue(in.readLine().startsWith("220")); // greeting (RFC 959)
			out.write("USER nobody\r\n");
			out.flush();
			assertTrue(in.readLine().startsWith("331"));
			long start = System.currentTimeMillis();
			out.write("PASS wrong password\r\n");
			out.flush();
			String reply = in.readLine();
			long elapsed = System.currentTimeMillis() - start;
			assertTrue(reply.startsWith("530"), "failed login: " + reply);
			assertTrue(elapsed >= 350, "failed login replied after only " + elapsed + "ms");
		} finally {
			server.setLoginFailureDelay(old);
		}
	}

	private static void deleteAll(File f) {
		if (f == null || !f.exists() && !Files.isSymbolicLink(f.toPath())) {
			return;
		}
		if (!Files.isSymbolicLink(f.toPath())) {
			File[] kids = f.listFiles();
			if (kids != null) {
				for (File k : kids) {
					deleteAll(k);
				}
			}
		}
		f.delete();
	}
}
