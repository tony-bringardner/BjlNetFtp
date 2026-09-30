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
