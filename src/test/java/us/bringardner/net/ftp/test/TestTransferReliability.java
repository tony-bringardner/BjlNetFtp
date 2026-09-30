package us.bringardner.net.ftp.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.core.ILogger.Level;
import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.net.framework.server.FileBasedAcl;
import us.bringardner.net.framework.server.IServer;
import us.bringardner.net.ftp.server.FtpServer;

/**
 * Regression tests for data-transfer reliability:
 * <ol>
 * <li>The preliminary (150) reply is always sent before the completion (226) reply,
 *     even for 0 and 1 byte files.</li>
 * <li>A stalled data connection (upload or download) is aborted with a 426 after the
 *     data timeout instead of hanging forever.</li>
 * <li>STOR never truncates or corrupts the existing file unless the upload completes.</li>
 * </ol>
 * Uses a raw socket control connection so the exact reply sequence can be checked.
 */
public class TestTransferReliability {

	private static final int PORT = 8031;
	private static final int DATA_TIMEOUT_MS = 1500;
	private static final String ROOT_PATH = "target/FtpRootReliability";

	private static FtpServer server;
	private static File rootDir;

	@BeforeAll
	public static void startServer() throws Exception {
		// Other test classes leave a file based ACL configured; this class uses the built in anonymous ACL.
		System.clearProperty(IServer.AUTHENTICATOION_PROVIDER_PROPERTY);
		System.clearProperty(FileBasedAcl.PROP_FILE_NAME);

		rootDir = new File(ROOT_PATH).getAbsoluteFile();
		deleteAll(rootDir);
		assertTrue(rootDir.mkdirs(), "can't create " + rootDir);

		FileSource root = FileSourceFactory.getDefaultFactory().createFileSource(rootDir.getAbsolutePath());
		server = new FtpServer();
		server.setSecure(false);
		server.setFtpRoot(root);
		server.setPort(PORT);
		server.setDataTimeout(DATA_TIMEOUT_MS);
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
		deleteAll(rootDir);
	}

	// ------------------------------------------------------------------ fix 1: reply order

	@Test
	public void emptyAndOneByteDownloadsReplyInOrder() throws Exception {
		try (Session s = new Session()) {
			s.put("empty.bin", new byte[0]);
			s.put("one.bin", new byte[] { 42 });

			for (int i = 0; i < 20; i++) {
				assertArrayEquals(new byte[0], s.get("empty.bin"), "empty file content, iteration " + i);
				assertArrayEquals(new byte[] { 42 }, s.get("one.bin"), "1 byte file content, iteration " + i);
			}
		}
	}

	// ------------------------------------------------------------------ fix 2: stalled transfers

	@Test
	public void stalledUploadTimesOutAndKeepsOriginal() throws Exception {
		byte[] original = "original content".getBytes(StandardCharsets.UTF_8);
		try (Session s = new Session()) {
			s.put("stall.txt", original);

			try (Socket data = s.pasv()) {
				s.send("STOR stall.txt");
				s.expectPreliminary();
				data.getOutputStream().write("partial".getBytes(StandardCharsets.UTF_8));
				data.getOutputStream().flush();
				// Now stall: keep the data connection open but send nothing more.
				long start = System.currentTimeMillis();
				Reply r = s.read(DATA_TIMEOUT_MS * 5);
				long elapsed = System.currentTimeMillis() - start;
				assertEquals(426, r.code, "stalled upload should be aborted: " + r);
				assertTrue(elapsed < DATA_TIMEOUT_MS * 4L, "timeout took too long: " + elapsed + "ms");
			}
			assertArrayEquals(original, s.get("stall.txt"), "original must survive a failed upload");
			assertNoTempFiles();
		}
	}

	@Test
	public void stalledDownloadTimesOut() throws Exception {
		byte[] big = new byte[32 * 1024 * 1024];
		try (Session s = new Session()) {
			s.put("big.bin", big);

			// Small receive buffer and never read, so the server's socket write blocks.
			try (Socket data = s.pasv(4096)) {
				s.send("RETR big.bin");
				s.expectPreliminary();
				long start = System.currentTimeMillis();
				Reply r = s.read(DATA_TIMEOUT_MS * 5);
				long elapsed = System.currentTimeMillis() - start;
				assertEquals(426, r.code, "stalled download should be aborted: " + r);
				assertTrue(elapsed < DATA_TIMEOUT_MS * 4L, "timeout took too long: " + elapsed + "ms");
			}
			// control connection is still usable
			s.send("NOOP");
			s.expect(200);
		}
	}

	// ------------------------------------------------------------------ fix 3: safe STOR

	@Test
	public void storWithoutDataConnectionDoesNotTruncate() throws Exception {
		byte[] original = "do not truncate me".getBytes(StandardCharsets.UTF_8);
		try (Session s = new Session()) {
			s.put("keep.txt", original);

			s.send("STOR keep.txt"); // no PASV/PORT first
			Reply r = s.read(5000);
			assertEquals(425, r.code, "STOR without a data connection: " + r);

			assertArrayEquals(original, s.get("keep.txt"), "file must be untouched");
			assertNoTempFiles();
		}
	}

	@Test
	public void abortedUploadKeepsOriginal() throws Exception {
		byte[] original = "abort original".getBytes(StandardCharsets.UTF_8);
		try (Session s = new Session()) {
			s.put("abort.txt", original);

			try (Socket data = s.pasv()) {
				s.send("STOR abort.txt");
				s.expectPreliminary();
				data.getOutputStream().write("half written".getBytes(StandardCharsets.UTF_8));
				data.getOutputStream().flush();
				s.send("ABOR");
				s.expect(426);
				s.expect(226);
			}
			assertArrayEquals(original, s.get("abort.txt"), "original must survive ABOR");
			assertNoTempFiles();
		}
	}

	@Test
	public void completedUploadReplacesFile() throws Exception {
		try (Session s = new Session()) {
			s.put("replace.txt", "old old old old".getBytes(StandardCharsets.UTF_8));
			byte[] updated = "new".getBytes(StandardCharsets.UTF_8);
			s.put("replace.txt", updated);
			assertArrayEquals(updated, s.get("replace.txt"));
			assertNoTempFiles();
		}
	}

	@Test
	public void restartedUploadAppends() throws Exception {
		try (Session s = new Session()) {
			s.put("rest.txt", "Hello ".getBytes(StandardCharsets.UTF_8));
			try (Socket data = s.pasv()) {
				s.send("REST 6");
				s.expect(350);
				s.send("STOR rest.txt");
				s.expectPreliminary();
				data.getOutputStream().write("World".getBytes(StandardCharsets.UTF_8));
			}
			s.expectComplete();
			assertArrayEquals("Hello World".getBytes(StandardCharsets.UTF_8), s.get("rest.txt"));
		}
	}

	// ------------------------------------------------------------------ PASV / EPSV

	@Test
	public void pasvRepliesWithoutDelay() throws Exception {
		try (Session s = new Session()) {
			int count = 20;
			long start = System.nanoTime();
			for (int i = 0; i < count; i++) {
				s.send("PASV");
				s.expect(227);
			}
			long avgMs = (System.nanoTime() - start) / 1_000_000 / count;
			// Previously each PASV polled in 200ms steps.
			assertTrue(avgMs < 100, "PASV took " + avgMs + "ms on average");
		}
	}

	@Test
	public void newPasvClosesPreviousListener() throws Exception {
		try (Session s = new Session()) {
			int first = s.pasvPort();
			s.pasvPort();
			assertPortClosed(first);
		}
	}

	@Test
	public void pasvListenerClosedWhenSessionEnds() throws Exception {
		int port;
		try (Session s = new Session()) {
			port = s.pasvPort();
		}
		assertPortClosed(port);
	}

	@Test
	public void abandonedPasvTimesOut() throws Exception {
		try (Session s = new Session()) {
			s.put("abandon.txt", "x".getBytes(StandardCharsets.UTF_8));
			int port = s.pasvPort(); // never connect
			long start = System.currentTimeMillis();
			s.send("RETR abandon.txt");
			Reply r = s.read(DATA_TIMEOUT_MS * 5);
			long elapsed = System.currentTimeMillis() - start;
			assertEquals(425, r.code, "RETR with no data connection: " + r);
			assertTrue(elapsed < DATA_TIMEOUT_MS * 4L, "took " + elapsed + "ms");
			assertPortClosed(port);
			// the session is still usable
			assertArrayEquals("x".getBytes(StandardCharsets.UTF_8), s.get("abandon.txt"));
		}
	}

	@Test
	public void pasvSkipsBusyPortsInRange() throws Exception {
		int oldMin = us.bringardner.net.ftp.server.PassiveSocket.getMinControlPort();
		int oldMax = us.bringardner.net.ftp.server.PassiveSocket.getMaxControlPort();
		try (java.net.ServerSocket busy = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
				Session s = new Session()) {
			int base = busy.getLocalPort();
			us.bringardner.net.ftp.server.PassiveSocket.setMaxControlPort(base + 2);
			us.bringardner.net.ftp.server.PassiveSocket.setMinControlPort(base);
			for (int i = 0; i < 6; i++) {
				int port = s.pasvPort();
				assertTrue(port == base + 1 || port == base + 2, "port " + port + " outside " + base + "-" + (base + 2) + " or busy");
			}
		} finally {
			us.bringardner.net.ftp.server.PassiveSocket.setMinControlPort(oldMin);
			us.bringardner.net.ftp.server.PassiveSocket.setMaxControlPort(oldMax);
		}
	}

	@Test
	public void epsvReplies229AndTransfers() throws Exception {
		byte[] content = "epsv content".getBytes(StandardCharsets.UTF_8);
		try (Session s = new Session()) {
			s.put("epsv.txt", content);

			s.send("EPSV 2");
			s.expect(522);

			s.send("EPSV");
			Reply r = s.expect(229);
			Matcher m = Pattern.compile("\\(\\|\\|\\|(\\d+)\\|\\)").matcher(r.text);
			assertTrue(m.find(), "bad EPSV reply " + r);
			int port = Integer.parseInt(m.group(1));
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			try (Socket data = new Socket()) {
				data.connect(new InetSocketAddress("127.0.0.1", port), 5000);
				data.setSoTimeout(10000);
				s.send("RETR epsv.txt");
				s.expectPreliminary();
				data.getInputStream().transferTo(buf);
			}
			s.expectComplete();
			assertArrayEquals(content, buf.toByteArray());

			s.send("EPSV ALL");
			s.expect(200);
		}
	}

	private static void assertPortClosed(int port) throws InterruptedException {
		// The server closes the listener asynchronously at session end; allow a moment.
		long end = System.currentTimeMillis() + 3000;
		while (true) {
			try (Socket probe = new Socket()) {
				probe.connect(new InetSocketAddress("127.0.0.1", port), 1000);
			} catch (IOException e) {
				return; // refused: closed
			}
			if (System.currentTimeMillis() > end) {
				fail("passive port " + port + " is still accepting connections");
			}
			Thread.sleep(50);
		}
	}

	// ------------------------------------------------------------------ helpers

	private static void assertNoTempFiles() throws IOException {
		List<String> found = new ArrayList<>();
		Files.walk(rootDir.toPath()).forEach(p -> {
			String n = p.getFileName().toString();
			if (n.endsWith(".ftp-part") || n.endsWith(".ftp-old")) {
				found.add(p.toString());
			}
		});
		assertTrue(found.isEmpty(), "temporary upload files left behind: " + found);
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

	static class Reply {
		final int code;
		final String text;

		Reply(int code, String text) {
			this.code = code;
			this.text = text;
		}

		@Override
		public String toString() {
			return code + " " + text;
		}
	}

	/** Minimal FTP control connection that exposes the exact reply sequence. */
	static class Session implements AutoCloseable {
		private static final Pattern PASV = Pattern.compile("\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\)");
		private final Socket control;
		private final BufferedReader in;
		private final Writer out;

		Session() throws IOException {
			control = new Socket();
			control.connect(new InetSocketAddress("127.0.0.1", PORT), 5000);
			control.setSoTimeout(10000);
			in = new BufferedReader(new InputStreamReader(control.getInputStream(), StandardCharsets.UTF_8));
			out = new OutputStreamWriter(control.getOutputStream(), StandardCharsets.UTF_8);
			expect(200); // greeting
			send("USER anonymous");
			expect(331);
			send("PASS test@example.com");
			expect(230);
			send("TYPE I");
			expect(200);
		}

		void send(String cmd) throws IOException {
			out.write(cmd + "\r\n");
			out.flush();
		}

		Reply read(int timeoutMs) throws IOException {
			int old = control.getSoTimeout();
			control.setSoTimeout(timeoutMs);
			try {
				String line = in.readLine();
				if (line == null) {
					throw new IOException("control connection closed");
				}
				int code = Integer.parseInt(line.substring(0, 3));
				StringBuilder text = new StringBuilder(line.substring(3));
				if (line.length() > 3 && line.charAt(3) == '-') {
					String end = line.substring(0, 3) + " ";
					String next;
					do {
						next = in.readLine();
						if (next == null) {
							throw new IOException("control connection closed");
						}
						text.append('\n').append(next);
					} while (!next.startsWith(end));
				}
				return new Reply(code, text.toString().trim());
			} catch (SocketTimeoutException e) {
				fail("no reply within " + timeoutMs + "ms");
				return null;
			} finally {
				control.setSoTimeout(old);
			}
		}

		Reply expect(int code) throws IOException {
			Reply r = read(10000);
			assertEquals(code, r.code, "unexpected reply: " + r);
			return r;
		}

		/** Any 1yz reply (RFC 959 allows 125 or 150 before a transfer). */
		Reply expectPreliminary() throws IOException {
			Reply r = read(10000);
			assertTrue(r.code >= 100 && r.code < 200, "expected a 1yz preliminary reply but got: " + r);
			return r;
		}

		/** Any 2yz reply (the transfer completed). */
		Reply expectComplete() throws IOException {
			Reply r = read(10000);
			assertTrue(r.code >= 200 && r.code < 300, "expected a 2yz completion reply but got: " + r);
			return r;
		}

		Socket pasv() throws IOException {
			return pasv(-1);
		}

		int pasvPort() throws IOException {
			send("PASV");
			Reply r = expect(227);
			Matcher m = PASV.matcher(r.text);
			assertTrue(m.find(), "bad PASV reply " + r);
			return Integer.parseInt(m.group(5)) * 256 + Integer.parseInt(m.group(6));
		}

		Socket pasv(int receiveBuffer) throws IOException {
			send("PASV");
			Reply r = expect(227);
			Matcher m = PASV.matcher(r.text);
			assertTrue(m.find(), "bad PASV reply " + r);
			int port = Integer.parseInt(m.group(5)) * 256 + Integer.parseInt(m.group(6));
			Socket data = new Socket();
			if (receiveBuffer > 0) {
				data.setReceiveBufferSize(receiveBuffer);
			}
			data.connect(new InetSocketAddress("127.0.0.1", port), 5000);
			data.setSoTimeout(10000);
			return data;
		}

		void put(String name, byte[] content) throws IOException {
			try (Socket data = pasv()) {
				send("STOR " + name);
				expectPreliminary();
				OutputStream o = data.getOutputStream();
				o.write(content);
				o.flush();
			}
			expectComplete();
		}

		byte[] get(String name) throws IOException {
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			try (Socket data = pasv()) {
				send("RETR " + name);
				expectPreliminary();
				InputStream i = data.getInputStream();
				byte[] b = new byte[8192];
				int got;
				while ((got = i.read(b)) >= 0) {
					buf.write(b, 0, got);
				}
			}
			expectComplete();
			return buf.toByteArray();
		}

		@Override
		public void close() throws IOException {
			try {
				send("QUIT");
			} catch (IOException e) {
				// ignore
			}
			control.close();
		}
	}
}
