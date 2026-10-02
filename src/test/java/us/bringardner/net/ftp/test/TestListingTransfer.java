package us.bringardner.net.ftp.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import us.bringardner.core.ILogger.Level;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.net.framework.server.FileBasedAcl;
import us.bringardner.net.framework.server.IServer;
import us.bringardner.net.ftp.server.FtpServer;

/**
 * LIST, NLST and MLSD over a raw control connection (BJL-30): every entry arrives, with
 * CRLF line ends, and a data connection the client drops gets 426 while the session goes on.
 * Listings used to be written one flush per entry (LIST) or built whole in memory (MLSD),
 * and a failed data connection ended the control session (or replied 551 for MLSD).
 */
public class TestListingTransfer {

	private static final int PORT = 8042;
	private static final int FILES = 3000;
	/**
	 * Long names, so even NLST of this directory (about 650 KB) is far bigger than the socket
	 * buffers. Names stay around 110 characters so the full path is under Windows' 260
	 * character limit.
	 */
	private static final int LONG_NAME_FILES = 6000;
	private static FtpServer server;
	private static File root;

	@BeforeAll
	public static void start() throws Exception {
		System.clearProperty(IServer.AUTHENTICATOION_PROVIDER_PROPERTY);
		System.clearProperty(FileBasedAcl.PROP_FILE_NAME);
		root = new File("target/FtpListingTransfer").getAbsoluteFile();
		File big = new File(root, "big");
		big.mkdirs();
		new File(root, "empty").mkdirs();
		File longNames = new File(root, "long");
		longNames.mkdirs();
		String pad = "x".repeat(100);
		for (int i = 0; i < LONG_NAME_FILES; i++) {
			File f = new File(longNames, String.format("%05d-%s.txt", i, pad));
			if (!f.exists()) {
				Files.write(f.toPath(), new byte[] { 'x' });
			}
		}
		for (int i = 0; i < FILES; i++) {
			File f = new File(big, String.format("file-%05d-été.txt", i));
			if (!f.exists()) {
				Files.write(f.toPath(), new byte[] { 'x' });
			}
		}
		server = new FtpServer();
		server.setSecure(false);
		server.setFtpRoot(FileSourceFactory.getDefaultFactory().createFileSource(root.getAbsolutePath()));
		server.setPort(PORT);
		server.getLogger().setLevel(Level.ERROR);
		server.start();
		long t = System.currentTimeMillis();
		while (!server.isRunning() && System.currentTimeMillis() - t < 5000) {
			Thread.sleep(20);
		}
		assertTrue(server.isRunning());
	}

	@AfterAll
	public static void stop() {
		if (server != null) {
			server.stop();
		}
	}

	@ParameterizedTest
	@CsvSource({ "LIST, big, 3000", "NLST, big, 3000", "MLSD, big, 3000", "LIST, empty, 0", "NLST, empty, 0", "MLSD, empty, 1" })
	public void everyEntryArrives(String command, String dir, int lines) throws Exception {
		try (Control c = new Control()) {
			int port = c.epsv();
			try (Socket data = new Socket(InetAddress.getLoopbackAddress(), port)) {
				c.send(command + " " + dir);
				assertEquals(150, c.code(c.reply()));
				String text = new String(readAll(data.getInputStream()), StandardCharsets.UTF_8);
				assertEquals(226, c.code(c.reply()));
				String[] got = text.isEmpty() ? new String[0] : text.split("\r\n", -1);
				// split leaves an empty string after the last CRLF
				int count = got.length == 0 ? 0 : got.length - 1;
				assertEquals(lines, count, command + " lines");
				assertTrue(text.isEmpty() || text.endsWith("\r\n"));
				assertEquals(-1, text.replace("\r\n", "").indexOf('\n'), "bare LF");
				if (lines == FILES) {
					assertTrue(text.contains("file-02999-été.txt"), "UTF-8 names");
				}
			}
			c.send("NOOP");
			assertEquals(200, c.code(c.reply()));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "LIST", "NLST", "MLSD" })
	public void droppedDataConnectionGets426AndTheSessionContinues(String command) throws Exception {
		try (Control c = new Control()) {
			int port = c.epsv();
			// A small receive window, so the server is still writing (a listing of about 650 KB)
			// when the connection is reset. Resetting only after the 150 reply: a connection reset
			// before the server accepts it is dropped from the accept queue on macOS/BSD, so the
			// server would just wait for another one.
			Socket data = new Socket();
			data.setReceiveBufferSize(4096);
			data.connect(new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), port), 5000);
			c.send(command + " long");
			assertEquals(150, c.code(c.reply()));
			data.setSoLinger(true, 0);
			data.close();
			assertEquals(426, c.code(c.reply()));
			c.send("NOOP");
			assertEquals(200, c.code(c.reply()), "the control connection must stay usable");
		}
	}

	private static byte[] readAll(InputStream in) throws IOException {
		ByteArrayOutputStream ret = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int n;
		while ((n = in.read(buf)) >= 0) {
			ret.write(buf, 0, n);
		}
		return ret.toByteArray();
	}

	/** A minimal FTP control connection, logged in as anonymous. */
	private static final class Control implements AutoCloseable {
		private static final Pattern EPSV = Pattern.compile("\\(\\|\\|\\|(\\d+)\\|\\)");
		private final Socket socket;
		private final BufferedReader in;
		private final OutputStream out;

		Control() throws IOException {
			socket = new Socket(InetAddress.getLoopbackAddress(), PORT);
			socket.setSoTimeout(10000);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
			out = socket.getOutputStream();
			assertEquals(220, code(reply()));
			send("USER anonymous");
			String r = reply();
			if (code(r) == 331) {
				send("PASS guest");
				r = reply();
			}
			assertEquals(230, code(r), r);
		}

		int epsv() throws IOException {
			send("EPSV");
			String r = reply();
			Matcher m = EPSV.matcher(r);
			assertTrue(code(r) == 229 && m.find(), r);
			return Integer.parseInt(m.group(1));
		}

		void send(String line) throws IOException {
			out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
			out.flush();
		}

		/** Reads a reply, skipping RFC 959 multi-line continuation lines. */
		String reply() throws IOException {
			String line = in.readLine();
			if (line != null && line.length() > 3 && line.charAt(3) == '-') {
				String end = line.substring(0, 3) + " ";
				String next;
				while ((next = in.readLine()) != null && !next.startsWith(end)) {
					// continuation
				}
				line = next;
			}
			assertTrue(line != null, "control connection closed");
			return line;
		}

		int code(String reply) {
			return Integer.parseInt(reply.substring(0, 3));
		}

		@Override
		public void close() throws IOException {
			socket.close();
		}
	}
}
