package us.bringardner.net.ftp.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.net.ftp.client.FtpClient;

/**
 * When the server accepts AUTH TLS but TLS can't be set up (here: its certificate is for another
 * host), FtpClient must not carry on and log in over the plain connection: that would send the
 * password in clear text, which is exactly what an attacker who breaks the TLS step wants.
 * A server that refuses AUTH (no TLS at all) still gets a plain login, unless requireSecure is set.
 */
public class TestFtpsNoPlaintextFallback {

	private static final String PASSWORD = "test-only";
	private static final String SECRET = "the-users-password";
	private static File dir;
	private static SSLContext otherNameCert;

	static class TrustAll implements X509TrustManager {
		@Override
		public void checkClientTrusted(X509Certificate[] chain, String authType) {
		}
		@Override
		public void checkServerTrusted(X509Certificate[] chain, String authType) {
		}
		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}
	}

	@BeforeAll
	public static void setUp() throws Exception {
		dir = Files.createTempDirectory("bjl-ftps-fallback").toFile();
		File ks = new File(dir, "other.p12");
		String keytool = System.getProperty("java.home")+File.separator+"bin"+File.separator+"keytool";
		Process p = new ProcessBuilder(keytool, "-genkeypair", "-noprompt", "-alias", "server",
				"-dname", "CN=bringardner.us", "-keystore", ks.getAbsolutePath(), "-storetype", "PKCS12",
				"-storepass", PASSWORD, "-keypass", PASSWORD, "-keyalg", "RSA", "-keysize", "2048", "-validity", "2")
				.redirectErrorStream(true).start();
		String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertTrue(p.waitFor(60, TimeUnit.SECONDS), "keytool did not finish");
		assertEquals(0, p.exitValue(), "keytool failed: "+out);
		KeyStore store = KeyStore.getInstance("PKCS12");
		try (InputStream in = new FileInputStream(ks)) {
			store.load(in, PASSWORD.toCharArray());
		}
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(store, PASSWORD.toCharArray());
		otherNameCert = SSLContext.getInstance("TLS");
		otherNameCert.init(kmf.getKeyManagers(), null, null);
	}

	@AfterAll
	public static void tearDown() {
		if( dir != null ) {
			for (File f : dir.listFiles()) {
				f.delete();
			}
			dir.delete();
		}
	}

	private static void reply(OutputStream out, String line) throws IOException {
		out.write((line+"\r\n").getBytes(StandardCharsets.US_ASCII));
		out.flush();
	}

	private static String readLine(InputStream in) throws IOException {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		int c;
		while( (c = in.read()) >= 0 && c != '\n' ) {
			if( c != '\r' ) {
				b.write(c);
			}
		}
		return c < 0 && b.size() == 0 ? null : b.toString(StandardCharsets.US_ASCII.name());
	}

	enum Auth {
		/** AUTH TLS accepted, then TLS with a certificate for another host (the client's check rejects it) */
		WRONG_CERTIFICATE,
		/** AUTH TLS accepted, then no TLS at all (what an attacker in the middle can do), and AUTH SSL refused */
		ACCEPTED_THEN_NO_TLS,
		/** AUTH refused: the server has no TLS */
		REFUSED
	}

	/**
	 * Greeting, then AUTH as given. Then it answers a login in plain text and returns everything
	 * the client sent in plain text after its first AUTH.
	 */
	private static CompletableFuture<String> fakeServer(ServerSocket ss, Auth mode) {
		CompletableFuture<String> plainAfterAuth = new CompletableFuture<>();
		Thread t = new Thread(() -> {
			ByteArrayOutputStream seen = new ByteArrayOutputStream();
			try (Socket s = ss.accept()) {
				s.setSoTimeout(3000);
				InputStream in = s.getInputStream();
				OutputStream out = s.getOutputStream();
				reply(out, "220 fake server");
				String line;
				boolean afterAuth = false;
				while( (line = readLine(in)) != null ) {
					if( afterAuth ) {
						seen.write((line+"\n").getBytes(StandardCharsets.ISO_8859_1));
					}
					String cmd = line.toUpperCase();
					if( cmd.startsWith("AUTH") ) {
						boolean first = !afterAuth;
						afterAuth = true;
						if( mode == Auth.ACCEPTED_THEN_NO_TLS ) {
							if( first ) {
								//  Accept, then answer the TLS handshake with something that isn't TLS
								reply(out, "234 go ahead");
								//  Exactly one bogus 5 byte record header: the client's TLS fails and nothing
								//  is left over, so the attacker controls what the client reads next
								out.write("xxxxx".getBytes(StandardCharsets.US_ASCII));
								out.flush();
								//  Skip the client's TLS hello: one record, a 5 byte header (type, version,
								//  length) and length bytes. What comes after it is plain text again.
								byte[] header = in.readNBytes(5);
								if( header.length == 5 ) {
									in.readNBytes(((header[3] & 0xff) << 8) | (header[4] & 0xff));
								}
							} else {
								reply(out, "502 no TLS here");
							}
						} else if( mode == Auth.WRONG_CERTIFICATE ) {
							reply(out, "234 go ahead");
							SSLSocket tls = (SSLSocket) otherNameCert.getSocketFactory().createSocket(s, null, s.getPort(), false);
							tls.setUseClientMode(false);
							try {
								tls.startHandshake();
							} catch (IOException e) {
								// the client rejected the certificate; keep listening on the plain socket
							}
						} else {
							reply(out, "502 no TLS here");
						}
					} else if( cmd.startsWith("USER") ) {
						reply(out, "331 password");
					} else if( cmd.startsWith("PASS") ) {
						reply(out, "230 logged in");
					} else if( cmd.startsWith("QUIT") ) {
						reply(out, "221 bye");
						break;
					} else if( !line.isEmpty() ) {
						reply(out, "502 not here");
					}
				}
			} catch (SocketTimeoutException e) {
				// the client went quiet
			} catch (IOException e) {
				// the client went away
			} finally {
				plainAfterAuth.complete(new String(seen.toByteArray(), StandardCharsets.ISO_8859_1));
			}
		});
		t.setDaemon(true);
		t.start();
		return plainAfterAuth;
	}

	private static FtpClient client(int port) {
		FtpClient client = new FtpClient("localhost", port);
		client.setTrustManagers(new TrustManager[] {new TrustAll()});
		client.setCmdTimeout(5000);
		client.setRequestSecure(true);
		client.setRequireSecure(false);
		return client;
	}

	@Test
	public void testFailedTlsAfterAuthDoesNotLogInInPlainText() throws Exception {
		for(Auth mode : new Auth[] {Auth.ACCEPTED_THEN_NO_TLS, Auth.WRONG_CERTIFICATE}) {
			try (ServerSocket ss = new ServerSocket(0, 5, InetAddress.getLoopbackAddress())) {
				CompletableFuture<String> plain = fakeServer(ss, mode);
				FtpClient client = client(ss.getLocalPort());
				try {
					assertThrows(IOException.class, () -> client.connect("user", SECRET, null),
							mode+": connect should fail when TLS fails after the server accepted AUTH");
				} finally {
					client.close();
				}
				String sent = plain.get(10, TimeUnit.SECONDS);
				assertFalse(sent.contains(SECRET), mode+": the password was sent in clear text after TLS failed: "+sent);
				assertFalse(sent.toUpperCase().contains("USER "), mode+": a login was attempted in clear text after TLS failed: "+sent);
				assertFalse(sent.toUpperCase().contains("AUTH SSL"), mode+": AUTH was tried again on the broken connection: "+sent);
			}
		}
	}

	@Test
	public void testServerWithoutTlsStillGetsAPlainLogin() throws Exception {
		try (ServerSocket ss = new ServerSocket(0, 5, InetAddress.getLoopbackAddress())) {
			CompletableFuture<String> plain = fakeServer(ss, Auth.REFUSED);
			FtpClient client = client(ss.getLocalPort());
			try {
				assertTrue(client.connect("user", SECRET, null), "Without requireSecure a server that refuses AUTH gets a plain login");
			} finally {
				client.close();
			}
			assertTrue(plain.get(10, TimeUnit.SECONDS).contains("USER user"));
		}
	}
}
