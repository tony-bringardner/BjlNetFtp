package us.bringardner.net.ftp.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.core.ILogger.Level;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.net.framework.server.FileBasedAcl;
import us.bringardner.net.framework.server.IServer;
import us.bringardner.net.ftp.client.FtpClient;
import us.bringardner.net.ftp.server.FtpServer;

/**
 * RFC 4217 data channel protection (PBSZ / PROT) for explicit (AUTH TLS) and implicit TLS.
 */
public class TestTlsDataChannel {

	private static final int EXPLICIT_PORT = 8034;
	private static final int IMPLICIT_PORT = 8035;
	private static final byte[] CONTENT = "tls data channel content".getBytes(StandardCharsets.UTF_8);

	private static FtpServer explicit, implicit;
	private static File root;
	private static SSLContext trustAll;

	@BeforeAll
	public static void start() throws Exception {
		File keystore = new File("target/serverkeystore.p12");
		TestFtpBaseTestClass.makeTestKeystore(keystore);
		System.setProperty("FtpServer.KeyStoreName", keystore.getPath());
		System.setProperty("FtpServer.KeyStorePassword", "peekab00");
		System.setProperty("FtpServer.KeyStoreType", "PKCS12");
		System.setProperty("FtpServer.Algorithm", "SunX509");
		System.clearProperty(IServer.AUTHENTICATOION_PROVIDER_PROPERTY);
		System.clearProperty(FileBasedAcl.PROP_FILE_NAME);

		root = new File("target/FtpTlsData").getAbsoluteFile();
		deleteAll(root);
		assertTrue(root.mkdirs());
		Files.write(new File(root, "file.txt").toPath(), CONTENT);

		explicit = startServer(EXPLICIT_PORT, false);
		implicit = startServer(IMPLICIT_PORT, true);

		trustAll = SSLContext.getInstance("TLS");
		trustAll.init(null, new TrustManager[] { new TestFtpBaseTestClass.TrustAll() }, null);
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
		deleteAll(root);
	}

	// ------------------------------------------------------------------ explicit (AUTH TLS)

	@Test
	public void explicitProtPUsesTlsDataConnection() throws Exception {
		try (Control c = Control.explicit(EXPLICIT_PORT)) {
			c.cmd("PBSZ 0", 200);
			c.cmd("PROT P", 200);
			assertArrayEquals(CONTENT, c.retr("file.txt", true));
		}
	}

	@Test
	public void explicitProtCUsesClearDataConnection() throws Exception {
		try (Control c = Control.explicit(EXPLICIT_PORT)) {
			c.cmd("PBSZ 0", 200);
			c.cmd("PROT C", 200);
			assertArrayEquals(CONTENT, c.retr("file.txt", false));
		}
	}

	@Test
	public void explicitDefaultIsClear() throws Exception {
		// RFC 4217: without PROT the data channel protection level is Clear
		try (Control c = Control.explicit(EXPLICIT_PORT)) {
			assertArrayEquals(CONTENT, c.retr("file.txt", false));
		}
	}

	@Test
	public void protCommandRules() throws Exception {
		try (Control plain = Control.plain(EXPLICIT_PORT)) {
			plain.cmd("PBSZ 0", 503); // no security exchange yet
			plain.cmd("PROT P", 503);
		}
		try (Control c = Control.explicit(EXPLICIT_PORT)) {
			c.cmd("PROT P", 503); // PBSZ first
			String r = c.cmd("PBSZ 1024", 200);
			assertTrue(r.contains("PBSZ=0"), "TLS always uses PBSZ=0: " + r);
			c.cmd("PROT S", 536);
			c.cmd("PROT E", 536);
			c.cmd("PROT X", 504);
		}
	}

	@Test
	public void clientNegotiatesProtectedDataChannel() throws Exception {
		FtpClient client = new FtpClient("localhost", EXPLICIT_PORT);
		client.setRequestSecure(true);
		client.setTrustManagers(new TrustManager[] { new TestFtpBaseTestClass.TrustAll() });
		client.getLogger().setLevel(Level.ERROR);
		try {
			assertTrue(client.connect("anonymous", "x", null));
			assertTrue(client.isChannelSecure(), "AUTH TLS failed");
			try (OutputStream out = client.getOutputStream("up-explicit.txt")) {
				out.write(CONTENT);
			}
			try (InputStream in = client.getInputStream("up-explicit.txt")) {
				assertArrayEquals(CONTENT, in.readAllBytes());
			}
			String dialog = client.dialog.toString();
			assertTrue(dialog.contains("Write:PBSZ 0") && dialog.contains("Write:PROT P"), dialog);
		} finally {
			client.close();
		}
	}

	@Test
	public void reconnectNegotiatesTlsAgain() throws Exception {
		FtpClient client = new FtpClient("localhost", EXPLICIT_PORT);
		client.setRequestSecure(true);
		client.setTrustManagers(new TrustManager[] { new TestFtpBaseTestClass.TrustAll() });
		client.getLogger().setLevel(Level.ERROR);
		try {
			assertTrue(client.connect("anonymous", "x", null));
			client.close();
			int before = count(client.dialog.toString(), "Write:AUTH TLS");
			// Before the fix, a reconnect skipped AUTH and sent USER/PASS in clear text
			assertTrue(client.connect("anonymous", "x", null));
			assertEquals(before + 1, count(client.dialog.toString(), "Write:AUTH TLS"), "reconnect did not send AUTH");
			assertTrue(client.isChannelSecure());
			try (InputStream in = client.getInputStream("file.txt")) {
				assertArrayEquals(CONTENT, in.readAllBytes());
			}
		} finally {
			client.close();
		}
	}

	private static int count(String text, String needle) {
		int n = 0;
		for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
			n++;
		}
		return n;
	}

	@Test
	public void activeModeWithProtectedDataChannel() throws Exception {
		FtpClient client = new FtpClient("localhost", EXPLICIT_PORT);
		client.setRequestSecure(true);
		client.setActive(true);
		client.setTrustManagers(new TrustManager[] { new TestFtpBaseTestClass.TrustAll() });
		client.getLogger().setLevel(Level.ERROR);
		try {
			assertTrue(client.connect("anonymous", "x", null));
			assertTrue(client.isDataChannelSecure());
			try (InputStream in = client.getInputStream("file.txt")) {
				assertArrayEquals(CONTENT, in.readAllBytes());
			}
		} finally {
			client.close();
		}
	}

	// ------------------------------------------------------------------ implicit TLS

	@Test
	public void implicitTlsDataIsProtectedByDefault() throws Exception {
		try (Control c = Control.implicit(IMPLICIT_PORT)) {
			assertArrayEquals(CONTENT, c.retr("file.txt", true));
		}
	}

	@Test
	public void clientWorksWithImplicitTls() throws Exception {
		FtpClient client = new FtpClient("localhost", IMPLICIT_PORT);
		client.setSecure(true);
		client.setTrustManagers(new TrustManager[] { new TestFtpBaseTestClass.TrustAll() });
		client.getLogger().setLevel(Level.ERROR);
		try {
			assertTrue(client.connect("anonymous", "x", null));
			try (OutputStream out = client.getOutputStream("up-implicit.txt")) {
				out.write(CONTENT);
			}
			try (InputStream in = client.getInputStream("up-implicit.txt")) {
				assertArrayEquals(CONTENT, in.readAllBytes());
			}
		} finally {
			client.close();
		}
	}

	// ------------------------------------------------------------------ helpers

	/** A control connection that can be plain, upgraded with AUTH TLS, or implicit TLS. */
	static class Control implements AutoCloseable {
		private static final Pattern PASV = Pattern.compile("\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\)");
		private Socket socket;
		private BufferedReader in;
		private Writer out;

		static Control plain(int port) throws IOException {
			Control c = new Control();
			Socket s = new Socket();
			s.connect(new InetSocketAddress("127.0.0.1", port), 5000);
			c.attach(s);
			c.expect(200);
			c.login();
			return c;
		}

		static Control explicit(int port) throws IOException {
			Control c = new Control();
			Socket s = new Socket();
			s.connect(new InetSocketAddress("127.0.0.1", port), 5000);
			c.attach(s);
			c.expect(200);
			c.cmd("AUTH TLS", 234);
			SSLSocket tls = (SSLSocket) trustAll.getSocketFactory().createSocket(s, "localhost", port, true);
			tls.setUseClientMode(true);
			tls.startHandshake();
			c.attach(tls);
			c.login();
			return c;
		}

		static Control implicit(int port) throws IOException {
			Control c = new Control();
			SSLSocket tls = (SSLSocket) trustAll.getSocketFactory().createSocket();
			tls.connect(new InetSocketAddress("127.0.0.1", port), 5000);
			tls.setUseClientMode(true);
			tls.startHandshake();
			c.attach(tls);
			c.expect(200);
			c.login();
			return c;
		}

		private void attach(Socket s) throws IOException {
			socket = s;
			s.setSoTimeout(10000);
			in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
			out = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8);
		}

		private void login() throws IOException {
			cmd("USER anonymous", 331);
			cmd("PASS x", 230);
			cmd("TYPE I", 200);
		}

		String cmd(String command, int expected) throws IOException {
			out.write(command + "\r\n");
			out.flush();
			String line = in.readLine();
			assertTrue(line != null, "connection closed after " + command);
			assertEquals(expected, Integer.parseInt(line.substring(0, 3)), command + " -> " + line);
			return line;
		}

		void expect(int code) throws IOException {
			String line = in.readLine();
			assertTrue(line != null && line.startsWith(String.valueOf(code)), "expected " + code + " got " + line);
		}

		byte[] retr(String name, boolean tls) throws IOException {
			String r = cmd("PASV", 227);
			Matcher m = PASV.matcher(r);
			assertTrue(m.find(), r);
			int port = Integer.parseInt(m.group(5)) * 256 + Integer.parseInt(m.group(6));
			Socket data;
			if (tls) {
				SSLSocket s = (SSLSocket) trustAll.getSocketFactory().createSocket();
				s.setUseClientMode(true);
				data = s;
			} else {
				data = new Socket();
			}
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			try (Socket d = data) {
				d.connect(new InetSocketAddress("127.0.0.1", port), 5000);
				d.setSoTimeout(5000);
				out.write("RETR " + name + "\r\n");
				out.flush();
				String prelim = in.readLine();
				assertTrue(prelim != null && prelim.startsWith("1"), "RETR -> " + prelim);
				if (tls) {
					((SSLSocket) d).startHandshake();
				}
				d.getInputStream().transferTo(buf);
			}
			expect(226);
			return buf.toByteArray();
		}

		@Override
		public void close() throws IOException {
			try {
				out.write("QUIT\r\n");
				out.flush();
			} catch (IOException e) {
				// ignore
			}
			socket.close();
		}
	}

	private static void deleteAll(File f) {
		if (f == null || !f.exists()) {
			return;
		}
		File[] kids = f.listFiles();
		if (kids != null) {
			for (File k : kids) {
				deleteAll(k);
			}
		}
		f.delete();
	}
}
