package us.bringardner.net.ftp.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.core.ILogger.Level;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.net.framework.server.FileBasedAcl;
import us.bringardner.net.framework.server.IServer;
import us.bringardner.net.ftp.client.ClientFtpResponse;
import us.bringardner.net.ftp.client.FtpClient;
import us.bringardner.net.ftp.server.FtpRequestProcessor;
import us.bringardner.net.ftp.server.FtpServer;

/**
 * Client correctness (and a few small server helpers).
 */
public class TestClientBehaviour {

	private static final int PORT = 8033;
	private static final int DATA_TIMEOUT_MS = 1500;
	private static FtpServer server;
	private static File root;

	@BeforeAll
	public static void startServer() throws Exception {
		System.clearProperty(IServer.AUTHENTICATOION_PROVIDER_PROPERTY);
		System.clearProperty(FileBasedAcl.PROP_FILE_NAME);
		root = new File("target/FtpClientBehaviour").getAbsoluteFile();
		deleteAll(root);
		assertTrue(root.mkdirs());
		Files.write(new File(root, "small.txt").toPath(), "hello".getBytes());
		Files.write(new File(root, "big.bin").toPath(), new byte[32 * 1024 * 1024]);

		server = new FtpServer();
		server.setSecure(false);
		server.setFtpRoot(FileSourceFactory.getDefaultFactory().createFileSource(root.getAbsolutePath()));
		server.setPort(PORT);
		server.setDataTimeout(DATA_TIMEOUT_MS);
		server.getLogger().setLevel(Level.ERROR);
		server.start();
		long start = System.currentTimeMillis();
		while (!server.isRunning() && System.currentTimeMillis() - start < 5000) {
			Thread.sleep(20);
		}
		assertTrue(server.isRunning());
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
		deleteAll(root);
	}

	private static FtpClient client() throws IOException {
		FtpClient c = new FtpClient("localhost", PORT);
		c.setRequestSecure(false);
		c.getLogger().setLevel(Level.ERROR);
		assertTrue(c.connect("anonymous", "x", null));
		return c;
	}

	@Test
	public void asciiDownloadSendsTypeA() throws Exception {
		FtpClient c = client();
		try {
			try (InputStream in = c.getInputStream("small.txt", true)) {
				in.readAllBytes();
			}
			assertTrue(c.dialog.toString().contains("Write:TYPE A"), "ascii flag was ignored: " + c.dialog);
		} finally {
			c.close();
		}
	}

	private static int count(String dialog, String text) {
		int n = 0;
		for (int i = dialog.indexOf(text); i >= 0; i = dialog.indexOf(text, i + 1)) {
			n++;
		}
		return n;
	}

	/** TYPE is sent only when it changes (BJL-34); it used to be sent before every transfer. */
	@Test
	public void typeIsSentOnlyWhenItChanges() throws Exception {
		FtpClient c = client();
		try {
			for (int i = 0; i < 3; i++) {
				try (InputStream in = c.getInputStream("small.txt")) {
					in.readAllBytes();
				}
			}
			assertEquals(1, count(c.dialog.toString(), "Write:TYPE I"), c.dialog.toString());
			try (InputStream in = c.getInputStream("small.txt", true)) {
				in.readAllBytes();
			}
			try (InputStream in = c.getInputStream("small.txt", true)) {
				in.readAllBytes();
			}
			assertEquals(1, count(c.dialog.toString(), "Write:TYPE A"));
			try (InputStream in = c.getInputStream("small.txt")) {
				in.readAllBytes();
			}
			assertEquals(2, count(c.dialog.toString(), "Write:TYPE I"));

			// a TYPE sent by hand makes the client send its own again
			c.executeCommand("TYPE A");
			try (InputStream in = c.getInputStream("small.txt")) {
				assertEquals("hello", new String(in.readAllBytes()));
			}
			assertEquals(3, count(c.dialog.toString(), "Write:TYPE I"));
		} finally {
			c.close();
		}
	}

	/** After a reconnect the server is in its default type again, so TYPE is sent again. */
	@Test
	public void typeIsSentAgainAfterReconnect() throws Exception {
		FtpClient c = client();
		try {
			try (InputStream in = c.getInputStream("small.txt")) {
				in.readAllBytes();
			}
			c.close();
			assertTrue(c.connect("anonymous", "x", null));
			try (InputStream in = c.getInputStream("small.txt")) {
				in.readAllBytes();
			}
			assertEquals(2, count(c.dialog.toString(), "Write:TYPE I"), c.dialog.toString());
		} finally {
			c.close();
		}
	}

	@Test
	public void closeTwiceIsHarmless() throws Exception {
		FtpClient c = client();
		try {
			InputStream in = c.getInputStream("small.txt");
			assertEquals("hello", new String(in.readAllBytes()));
			in.close();
			in.close(); // used to read a second reply and desynchronize the control connection
			assertTrue(c.executePwd() != null);
			try (InputStream again = c.getInputStream("small.txt")) {
				assertEquals("hello", new String(again.readAllBytes()));
			}
		} finally {
			c.close();
		}
	}

	@Test
	public void earlyCloseIsNotAnError() throws Exception {
		FtpClient c = client();
		try {
			try (InputStream in = c.getInputStream("big.bin")) {
				in.read(new byte[1024]);
			} // abandoning a download is allowed
			try (InputStream in = c.getInputStream("small.txt")) {
				assertEquals("hello", new String(in.readAllBytes()));
			}
		} finally {
			c.close();
		}
	}

	/** Runs a listing in passive mode and returns its data socket's receive buffer size. */
	private static int passiveDataSocketBuffer(int socketBufferSize) throws Exception {
		java.util.concurrent.atomic.AtomicInteger size = new java.util.concurrent.atomic.AtomicInteger(-1);
		FtpClient c = new FtpClient("localhost", PORT) {
			@Override
			protected us.bringardner.net.ftp.client.ClientDataTransferProcess getDataTransferProcess() throws IOException {
				us.bringardner.net.ftp.client.ClientDataTransferProcess ret = super.getDataTransferProcess();
				// passive mode connects before the command anyway
				size.set(ret.getSocket().getReceiveBufferSize());
				return ret;
			}
		};
		c.setRequestSecure(false);
		c.getLogger().setLevel(Level.ERROR);
		c.setSocketBufferSize(socketBufferSize);
		assertTrue(c.connect("anonymous", "x", null));
		try {
			// a listing opens the data connection through getDataTransferProcess
			c.executeList();
			return size.get();
		} finally {
			c.close();
		}
	}

	/**
	 * Data sockets keep the OS's buffer size (TCP autotuning) unless one is configured (BJL-29).
	 * A fixed 65 KB buffer used to cap transfers at about 1.4 MB/s at 50 ms round-trip time.
	 */
	@Test
	public void dataSocketsUseTheOsBufferSizeByDefault() throws Exception {
		// The OS default for a connected loopback socket (an unconnected socket can report a
		// different value: on macOS 131072 before connect, 408300 after)
		int osDefault;
		try (java.net.ServerSocket ss = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
				java.net.Socket plain = new java.net.Socket(java.net.InetAddress.getLoopbackAddress(), ss.getLocalPort());
				java.net.Socket accepted = ss.accept()) {
			osDefault = plain.getReceiveBufferSize();
		}
		assertEquals(osDefault, passiveDataSocketBuffer(0), "receive buffer left to the OS");

		int configured = passiveDataSocketBuffer(16384);
		assertTrue(configured >= 16384 && configured != osDefault, "configured receive buffer: "+configured);
	}

	@Test
	public void socketBufferSizeMustNotBeNegative() {
		FtpClient c = new FtpClient("localhost", PORT);
		assertEquals(0, c.getSocketBufferSize());
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> c.setSocketBufferSize(-1));
	}

	@Test
	public void truncatedDownloadIsReported() throws Exception {
		FtpClient c = client();
		c.setTransferBufferSize(4096);
		try {
			IOException failure = null;
			long total = 0;
			try (InputStream in = c.getInputStream("big.bin")) {
				byte[] buf = new byte[8192];
				total += in.read(buf);
				// stall longer than the server's data timeout; the server aborts with 426
				Thread.sleep(DATA_TIMEOUT_MS * 2L);
				int r;
				while ((r = in.read(buf)) >= 0) {
					total += r;
				}
			} catch (IOException e) {
				failure = e; // from read() (reset) or close() (426 after EOF)
			}
			assertTrue(failure != null, "a truncated download (" + total + " of " + (32 * 1024 * 1024)
					+ " bytes) was reported as success");
		} finally {
			c.close();
		}
	}

	@Test
	public void malformedReplyIsAnIOException() {
		FtpClient fake = new FtpClient("localhost", PORT) {
			@Override
			protected String readLine() {
				return "garbage reply";
			}
		};
		assertThrows(IOException.class, () -> new ClientFtpResponse().readResponse(fake));
	}

	@Test
	public void skipFullyHandlesShortSkips() throws Exception {
		byte[] data = new byte[1000];
		Arrays.fill(data, (byte) 7);
		// skip() never skips anything, like some network/decompressing streams
		InputStream lazy = new ByteArrayInputStream(data) {
			@Override
			public synchronized long skip(long n) {
				return 0;
			}
		};
		assertEquals(600, FtpRequestProcessor.skipFully(lazy, 600));
		assertEquals(400, lazy.available());
		assertEquals(400, FtpRequestProcessor.skipFully(lazy, 999), "stops at end of stream");
	}

	@Test
	public void rootConstructorUsesRoot() throws Exception {
		FileSource dir = FileSourceFactory.getDefaultFactory().createFileSource(root.getAbsolutePath());
		FtpServer s = new FtpServer(dir, false);
		assertEquals(dir.getCanonicalPath(), s.getFtpRoot().getCanonicalPath());
	}

	// ------------------------------------------------------------------ EPSV / active / one transfer / reconnect

	@Test
	public void usesEpsvByDefault() throws Exception {
		FtpClient c = client();
		try {
			try (InputStream in = c.getInputStream("small.txt")) {
				assertEquals("hello", new String(in.readAllBytes()));
			}
			String d = c.dialog.toString();
			assertTrue(d.contains("Write:EPSV") && !d.contains("Write:PASV"), d);
		} finally {
			c.close();
		}
	}

	@Test
	public void fallsBackToPasvWhenEpsvIsUnsupported() throws Exception {
		try (FakeFtpServer fake = new FakeFtpServer()) {
			fake.supportEpsv = false;
			FtpClient c = fakeClient(fake);
			try {
				for (int i = 0; i < 2; i++) {
					try (InputStream in = c.getInputStream("x")) {
						assertEquals("fake data", new String(in.readAllBytes()));
					}
				}
				java.util.List<String> cmds = fake.commands(1);
				assertEquals(1, cmds.stream().filter(x -> x.startsWith("EPSV")).count(), "EPSV should be tried once: " + cmds);
				assertEquals(2, cmds.stream().filter(x -> x.startsWith("PASV")).count(), cmds.toString());
			} finally {
				c.close();
			}
		}
	}

	@Test
	public void activeModeTransfers() throws Exception {
		FtpClient c = client();
		c.setActive(true);
		try {
			try (java.io.OutputStream out = c.getOutputStream("active-up.txt")) {
				out.write("active".getBytes());
			}
			try (InputStream in = c.getInputStream("active-up.txt")) {
				assertEquals("active", new String(in.readAllBytes()));
			}
			assertTrue(c.dialog.toString().contains("Write:PORT 127,0,0,1,"), c.dialog.toString());
		} finally {
			c.close();
		}
	}

	@Test
	public void secondTransferOnSameConnectionIsRefused() throws Exception {
		FtpClient c = client();
		try {
			InputStream first = c.getInputStream("small.txt");
			assertThrows(IOException.class, () -> c.getInputStream("big.bin"), "second stream");
			assertThrows(IOException.class, () -> c.executeSize("small.txt"), "command while streaming");
			assertEquals("hello", new String(first.readAllBytes()));
			first.close();
			try (InputStream in = c.getInputStream("small.txt")) {
				assertEquals("hello", new String(in.readAllBytes()));
			}
		} finally {
			c.close();
		}
	}

	@Test
	public void reconnectsAndRestoresDirectoryWhenServerDropsConnection() throws Exception {
		try (FakeFtpServer fake = new FakeFtpServer()) {
			FtpClient c = fakeClient(fake);
			try {
				assertTrue(c.setCurrentDir("/some/dir"));
				fake.dropFirstConnectionOn = "NOOP"; // like an idle timeout
				assertTrue(c.executeCommand("NOOP").isPositiveComplet(), "NOOP should be retried on a new connection");
				java.util.List<String> second = fake.commands(2);
				assertTrue(second.contains("CWD /some/dir"), "directory not restored: " + second);
				assertEquals("/some/dir", c.getCurrentDir());
			} finally {
				c.close();
			}
		}
	}

	private static FtpClient fakeClient(FakeFtpServer fake) throws IOException {
		FtpClient c = new FtpClient("127.0.0.1", fake.port());
		c.setRequestSecure(false);
		c.getLogger().setLevel(Level.ERROR);
		assertTrue(c.connect("anonymous", "x", null));
		return c;
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
