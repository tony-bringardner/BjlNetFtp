package us.bringardner.net.ftp.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import us.bringardner.core.ILogger.Level;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.net.framework.server.FileBasedAcl;
import us.bringardner.net.framework.server.IServer;
import us.bringardner.net.ftp.client.FtpClient;
import us.bringardner.net.ftp.server.FtpServer;

/**
 * BJL-18: FtpClient's protected data connections resume the control connection's TLS
 * session, as RFC 4217 recommends and servers such as vsftpd (require_ssl_reuse) and
 * FileZilla Server require.
 * <p>
 * A resumed handshake doesn't send the server's certificate, so the client's trust manager
 * is only asked to check it for full handshakes: one check (the control connection) for a
 * whole session of transfers means every data connection was resumed.
 */
public class TestTlsResumption {

	/** Explicit TLS (AUTH TLS). */
	private static final int EXPLICIT_PORT = 8040;
	/** Implicit TLS; the client chooses the protocol version here. */
	private static final int IMPLICIT_PORT = 8041;
	private static final byte[] CONTENT = "resumed".getBytes(StandardCharsets.UTF_8);
	private static FtpServer explicit, implicit;
	private static String oldServerProtocol;
	private static File root;

	/** Counts full handshakes (certificate checks) and records the protocol they used. */
	static final class CountingTrust extends X509ExtendedTrustManager {
		final AtomicInteger checks = new AtomicInteger();
		final List<String> protocols = Collections.synchronizedList(new ArrayList<>());

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
			checks.incrementAndGet();
			if (socket instanceof SSLSocket && ((SSLSocket) socket).getHandshakeSession() != null) {
				protocols.add(((SSLSocket) socket).getHandshakeSession().getProtocol());
			}
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
			checks.incrementAndGet();
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) {
			checks.incrementAndGet();
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
		}

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) {
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}
	}

	@BeforeAll
	public static void start() throws Exception {
		File keystore = new File(TestFtpBaseTestClass.TEST_KEYSTORE);
		TestFtpBaseTestClass.makeTestKeystore(keystore);
		System.setProperty("FtpServer.KeyStoreName", keystore.getPath());
		System.setProperty("FtpServer.KeyStorePassword", TestFtpBaseTestClass.TEST_KEYSTORE_PASSWORD);
		System.setProperty("FtpServer.KeyStoreType", "PKCS12");
		System.setProperty("FtpServer.Algorithm", "SunX509");
		System.clearProperty(IServer.AUTHENTICATOION_PROVIDER_PROPERTY);
		System.clearProperty(FileBasedAcl.PROP_FILE_NAME);
		root = new File("target/FtpTlsResumption").getAbsoluteFile();
		root.mkdirs();
		Files.write(new File(root, "file.txt").toPath(), CONTENT);

		// A configured protocol that differs from the AUTH name ("TLS") once made the server
		// run AUTH on a different TLS context from its data connections, so the first data
		// connection couldn't resume; keep that setup covered.
		oldServerProtocol = System.getProperty("FtpServer.Protocol");
		System.setProperty("FtpServer.Protocol", "TLSv1.3");
		explicit = startServer(EXPLICIT_PORT, false);
		implicit = startServer(IMPLICIT_PORT, true);
	}

	private static FtpServer startServer(int port, boolean secure) throws Exception {
		FtpServer s = new FtpServer();
		s.setSecure(secure);
		s.setFtpRoot(FileSourceFactory.getDefaultFactory().createFileSource(root.getAbsolutePath()));
		s.setPort(port);
		s.setDataTimeout(5000);
		s.getLogger().setLevel(Level.ERROR);
		s.start();
		long start = System.currentTimeMillis();
		while (!s.isRunning() && System.currentTimeMillis() - start < 5000) {
			Thread.sleep(20);
		}
		assertTrue(s.isRunning());
		return s;
	}

	@AfterAll
	public static void stop() throws Exception {
		for (FtpServer s : new FtpServer[] { explicit, implicit }) {
			if (s != null) {
				s.stop();
			}
		}
		if (oldServerProtocol == null) {
			System.clearProperty("FtpServer.Protocol");
		} else {
			System.setProperty("FtpServer.Protocol", oldServerProtocol);
		}
	}

	@ParameterizedTest(name = "{0} {1}, active={2}")
	@CsvSource({
		"explicit, TLSv1.3, false", "explicit, TLSv1.3, true",
		"implicit, TLSv1.2, false", "implicit, TLSv1.2, true",
		"implicit, TLSv1.3, false" })
	public void dataConnectionsResumeTheControlSession(String mode, String protocol, boolean active) throws Exception {
		CountingTrust trust = new CountingTrust();
		FtpClient client;
		if (mode.equals("explicit")) {
			client = new FtpClient("localhost", EXPLICIT_PORT);
			client.setRequestSecure(true);
		} else {
			client = new FtpClient("localhost", IMPLICIT_PORT);
			client.setSecure(true);
			client.setProtocol(protocol);
		}
		client.setActive(active);
		client.setTrustManagers(new TrustManager[] { trust });
		client.getLogger().setLevel(Level.ERROR);
		try {
			assertTrue(client.connect("anonymous", "x", null));
			assertTrue(client.isDataChannelSecure());
			assertEquals(1, trust.checks.get(), "the control connection's handshake");
			assertEquals(protocol, trust.protocols.get(0));
			String name = "up-" + mode + "-" + protocol + "-" + active + ".txt";
			for (int idx = 0; idx < 3; idx++) {
				try (InputStream in = client.getInputStream("file.txt")) {
					assertArrayEquals(CONTENT, in.readAllBytes());
				}
				client.executeList();
				try (OutputStream out = client.getOutputStream(name)) {
					out.write(CONTENT);
				}
				assertArrayEquals(CONTENT, Files.readAllBytes(new File(root, name).toPath()));
			}
			assertEquals(1, trust.checks.get(), "full handshakes (1 = every data connection resumed): " + trust.protocols);
		} finally {
			client.close();
		}
	}
}
