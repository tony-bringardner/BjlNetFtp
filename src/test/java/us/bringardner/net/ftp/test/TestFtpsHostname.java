package us.bringardner.net.ftp.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
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
 * FtpClient checks that an FTPS server's certificate was issued for the host it connects to,
 * with explicit (AUTH TLS) and implicit TLS. The client trusts every certificate, so only the
 * host name check can reject one. A minimal fake server is used so the certificate is the only
 * thing that differs: it records whether the TLS handshake completed.
 */
public class TestFtpsHostname {

	private static final String PASSWORD = "test-only";
	private static File dir;
	private static SSLContext localhostCert;
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

	private static SSLContext serverContext(String name, String dname, String san) throws Exception {
		File ks = new File(dir, name);
		String keytool = System.getProperty("java.home")+File.separator+"bin"+File.separator+"keytool";
		ProcessBuilder pb = new ProcessBuilder(keytool, "-genkeypair", "-noprompt",
				"-alias", "server", "-dname", dname,
				"-keystore", ks.getAbsolutePath(), "-storetype", "PKCS12",
				"-storepass", PASSWORD, "-keypass", PASSWORD,
				"-keyalg", "RSA", "-keysize", "2048", "-validity", "2");
		if( san != null ) {
			pb.command().add("-ext");
			pb.command().add("SAN="+san);
		}
		pb.redirectErrorStream(true);
		Process p = pb.start();
		String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertTrue(p.waitFor(60, TimeUnit.SECONDS), "keytool did not finish");
		assertEquals(0, p.exitValue(), "keytool failed: "+out);

		KeyStore store = KeyStore.getInstance("PKCS12");
		try (InputStream in = new FileInputStream(ks)) {
			store.load(in, PASSWORD.toCharArray());
		}
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(store, PASSWORD.toCharArray());
		SSLContext ctx = SSLContext.getInstance("TLS");
		ctx.init(kmf.getKeyManagers(), null, null);
		return ctx;
	}

	@BeforeAll
	public static void setUp() throws Exception {
		dir = Files.createTempDirectory("bjl-ftps-host").toFile();
		localhostCert = serverContext("localhost.p12", "CN=localhost", "dns:localhost,ip:127.0.0.1");
		otherNameCert = serverContext("other.p12", "CN=bringardner.us", null);
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

	/**
	 * One connection: greeting, then (explicit) AUTH TLS and TLS on the connection, or (implicit)
	 * TLS from the start. Then it answers a login so a client that got through doesn't wait.
	 * @return whether the server's TLS handshake completed
	 */
	private static CompletableFuture<Boolean> fakeServer(ServerSocket ss, SSLContext cert, boolean implicit) {
		CompletableFuture<Boolean> handshake = new CompletableFuture<>();
		Thread t = new Thread(() -> {
			try (Socket plain = ss.accept()) {
				plain.setSoTimeout(5000);
				Socket s = plain;
				if( implicit ) {
					SSLSocket tls = (SSLSocket) cert.getSocketFactory().createSocket(plain, null, plain.getPort(), true);
					tls.setUseClientMode(false);
					s = startHandshake(tls, handshake);
					if( s == null ) {
						return;
					}
				}
				reply(s.getOutputStream(), "220 fake server");
				BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
				String line;
				while( (line = in.readLine()) != null ) {
					String cmd = line.toUpperCase();
					if( cmd.startsWith("AUTH") ) {
						reply(s.getOutputStream(), "234 go ahead");
						SSLSocket tls = (SSLSocket) cert.getSocketFactory().createSocket(s, null, s.getPort(), true);
						tls.setUseClientMode(false);
						s = startHandshake(tls, handshake);
						if( s == null ) {
							return;
						}
						in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
					} else if( cmd.startsWith("USER") ) {
						reply(s.getOutputStream(), "331 password");
					} else if( cmd.startsWith("PASS") ) {
						reply(s.getOutputStream(), "230 logged in");
					} else if( cmd.startsWith("QUIT") ) {
						reply(s.getOutputStream(), "221 bye");
						break;
					} else {
						reply(s.getOutputStream(), "502 not here");
					}
				}
			} catch (IOException e) {
				// the client went away
			} finally {
				handshake.complete(false);
			}
		});
		t.setDaemon(true);
		t.start();
		return handshake;
	}

	private static Socket startHandshake(SSLSocket tls, CompletableFuture<Boolean> handshake) {
		try {
			tls.startHandshake();
			handshake.complete(true);
			return tls;
		} catch (IOException e) {
			handshake.complete(false);
			return null;
		}
	}

	/** @return whether the server saw a completed TLS handshake */
	private static boolean tryConnect(SSLContext cert, boolean implicit, String host, Boolean verify) throws Exception {
		try (ServerSocket ss = new ServerSocket(0, 5, InetAddress.getLoopbackAddress())) {
			CompletableFuture<Boolean> handshake = fakeServer(ss, cert, implicit);
			FtpClient client = new FtpClient(host, ss.getLocalPort());
			client.setTrustManagers(new TrustManager[] {new TrustAll()});
			client.setCmdTimeout(5000);
			if( implicit ) {
				client.setSecure(true);
			} else {
				client.setRequestSecure(true);
				client.setRequireSecure(true);
			}
			if( verify != null ) {
				client.setVerifyHostname(verify);
			}
			try {
				client.connect("user", "secret", null);
			} catch (Exception e) {
				// a rejected certificate shows up as a failed connect
			} finally {
				try {
					client.close();
				} catch (Exception e) {
				}
			}
			Boolean ret = handshake.get(10, TimeUnit.SECONDS);
			assertNotNull(ret);
			return ret;
		}
	}

	@Test
	public void testVerifyHostnameIsOnByDefault() {
		assertTrue(new FtpClient("localhost").isVerifyHostname());
	}

	@Test
	public void testExplicitTlsChecksTheHostName() throws Exception {
		assertTrue(tryConnect(localhostCert, false, "localhost", null), "A certificate for localhost is accepted");
		assertFalse(tryConnect(otherNameCert, false, "localhost", null), "A certificate for another host must be rejected");
		assertTrue(tryConnect(otherNameCert, false, "localhost", false), "With the check off any certificate is accepted");
	}

	@Test
	public void testImplicitTlsChecksTheHostName() throws Exception {
		assertTrue(tryConnect(localhostCert, true, "localhost", null), "A certificate for localhost is accepted");
		assertFalse(tryConnect(otherNameCert, true, "localhost", null), "A certificate for another host must be rejected");
		assertTrue(tryConnect(otherNameCert, true, "localhost", false), "With the check off any certificate is accepted");
	}
}
