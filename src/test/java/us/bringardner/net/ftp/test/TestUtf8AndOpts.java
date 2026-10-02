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
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.core.ILogger.Level;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.net.framework.server.FileBasedAcl;
import us.bringardner.net.framework.server.IServer;
import us.bringardner.net.ftp.server.FtpServer;

/**
 * BJL-50: the server says it uses UTF-8 path names (RFC 2640 FEAT "UTF8"), accepts
 * "OPTS UTF8 ON", and a non-ASCII file name survives STOR, LIST, NLST, MLSD, MLST and RETR.
 * OPTS MLST follows RFC 3659 section 7.9 and really changes the facts MLSx sends.
 */
public class TestUtf8AndOpts {

	private static final int PORT = 8043;
	/** Latin, Greek, CJK and a character outside the BMP (4 UTF-8 bytes). */
	private static final String NAME = "été-Ωμέγα-日本語-😀.txt";
	private static FtpServer server;
	private static File root;

	@BeforeAll
	public static void start() throws Exception {
		System.clearProperty(IServer.AUTHENTICATOION_PROVIDER_PROPERTY);
		System.clearProperty(FileBasedAcl.PROP_FILE_NAME);
		root = new File("target/TestUtf8AndOpts").getAbsoluteFile();
		root.mkdirs();
		File old = new File(root, NAME);
		if (old.exists()) {
			assertTrue(old.delete());
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
		assertTrue(server.isRunning(), "server did not start");
	}

	@AfterAll
	public static void stop() throws Exception {
		if (server != null) {
			server.stop();
			long t = System.currentTimeMillis();
			while (server.isRunning() && System.currentTimeMillis() - t < 10000) {
				Thread.sleep(10);
			}
		}
	}

	@Test
	public void featListsUtf8() throws Exception {
		try (Control c = new Control()) {
			List<String> feat = c.send("FEAT");
			assertEquals("211-Extensions supported:", feat.get(0));
			assertTrue(feat.contains(" UTF8"), "FEAT should list UTF8: " + feat);
			assertEquals("211 End", feat.get(feat.size() - 1));
		}
	}

	@Test
	public void optsUtf8() throws Exception {
		try (Control c = new Control()) {
			assertEquals(200, c.code("OPTS UTF8 ON"));
			assertEquals(200, c.code("opts utf8 on"));
			assertEquals(200, c.code("OPTS UTF8"));
			assertEquals(504, c.code("OPTS UTF8 OFF"));
			assertEquals(501, c.code("OPTS UTF8 MAYBE"));
			assertEquals(501, c.code("OPTS NOSUCH ON"));
			assertEquals(501, c.code("OPTS"));
			assertEquals(200, c.code("NOOP"));
		}
	}

	@Test
	public void optsMlstSelectsFacts() throws Exception {
		try (Control c = new Control()) {
			c.store("facts.txt", "abc".getBytes(StandardCharsets.UTF_8));

			// Case-insensitive, unsupported facts ignored, reply lists the selection
			List<String> r = c.send("OPTS MLST size;frogs;Type;");
			assertEquals(1, r.size());
			assertEquals("200 MLST OPTS SIZE;TYPE;", r.get(0));
			assertEquals(" SIZE=3;TYPE=file; /facts.txt", c.send("MLST facts.txt").get(1));
			String mlstFeat = c.featMlst();
			assertTrue(mlstFeat.contains("SIZE*;") && mlstFeat.contains("TYPE*;")
					&& !mlstFeat.contains("MODIFY*") && !mlstFeat.contains("PERM*"), mlstFeat);

			// A syntax error leaves the selection unchanged
			assertEquals(501, c.code("OPTS MLST size; type;"));
			assertEquals(" SIZE=3;TYPE=file; /facts.txt", c.send("MLST facts.txt").get(1));

			// No list selects no facts: two spaces before the pathname (RFC 3659 7.9.2)
			assertEquals("200 MLST OPTS", c.send("OPTS MLST").get(0));
			assertEquals("  /facts.txt", c.send("MLST facts.txt").get(1));
			assertEquals("200 MLST OPTS", c.send("OPTS MLST frogs;").get(0));
			assertEquals(200, c.code("NOOP"));
		}
	}

	@Test
	public void nonAsciiNameRoundTrips() throws Exception {
		byte[] data = "contents\n".getBytes(StandardCharsets.UTF_8);
		try (Control c = new Control()) {
			assertEquals(200, c.code("OPTS UTF8 ON"));
			c.store(NAME, data);
			assertTrue(new File(root, NAME).isFile(), "stored under the right name on disk");

			assertTrue(c.listing("NLST").contains(NAME), "NLST");
			assertTrue(c.listing("LIST").stream().anyMatch(l -> l.endsWith(" " + NAME)), "LIST");
			assertTrue(c.listing("MLSD").stream().anyMatch(l -> l.endsWith("; " + NAME)), "MLSD");
			List<String> mlst = c.send("MLST " + NAME);
			assertEquals(250, code(mlst.get(0)), mlst.toString());
			assertTrue(mlst.get(1).endsWith("; /" + NAME), mlst.toString());
			assertEquals("213 " + data.length, c.send("SIZE " + NAME).get(0));

			assertArrayEquals(data, c.retrieve(NAME));
			assertEquals(250, c.code("DELE " + NAME));
		}
	}

	private static int code(String line) {
		return Integer.parseInt(line.substring(0, 3));
	}

	/** A raw control connection, logged in as anonymous, that returns every reply line. */
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
			List<String> greeting = read();
			assertEquals(220, TestUtf8AndOpts.code(greeting.get(0)), greeting.toString());
			List<String> r = send("USER anonymous");
			if (TestUtf8AndOpts.code(r.get(0)) == 331) {
				r = send("PASS guest");
			}
			assertEquals(230, TestUtf8AndOpts.code(r.get(0)), r.toString());
		}

		List<String> send(String line) throws IOException {
			out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
			out.flush();
			return read();
		}

		int code(String line) throws IOException {
			return TestUtf8AndOpts.code(send(line).get(0));
		}

		String featMlst() throws IOException {
			for (String l : send("FEAT")) {
				if (l.startsWith(" MLST ")) {
					return l;
				}
			}
			return "";
		}

		/** One reply: a single line, or "ddd-" ... "ddd " (RFC 959 4.2). */
		List<String> read() throws IOException {
			List<String> lines = new ArrayList<>();
			String line = in.readLine();
			assertTrue(line != null, "control connection closed");
			lines.add(line);
			if (line.length() > 3 && line.charAt(3) == '-') {
				String end = line.substring(0, 3) + " ";
				do {
					line = in.readLine();
					assertTrue(line != null, "closed inside a multi-line reply");
					lines.add(line);
				} while (!line.startsWith(end));
			}
			return lines;
		}

		private Socket data() throws IOException {
			List<String> r = send("EPSV");
			Matcher m = EPSV.matcher(r.get(0));
			assertTrue(TestUtf8AndOpts.code(r.get(0)) == 229 && m.find(), r.toString());
			Socket s = new Socket(InetAddress.getLoopbackAddress(), Integer.parseInt(m.group(1)));
			s.setSoTimeout(10000);
			return s;
		}

		void store(String name, byte[] content) throws IOException {
			assertEquals(200, code("TYPE I"));
			try (Socket s = data()) {
				List<String> r = send("STOR " + name);
				assertEquals(150, TestUtf8AndOpts.code(r.get(0)), r.toString());
				s.getOutputStream().write(content);
			}
			List<String> done = read();
			assertEquals(226, TestUtf8AndOpts.code(done.get(0)), done.toString());
		}

		byte[] retrieve(String name) throws IOException {
			assertEquals(200, code("TYPE I"));
			byte[] ret;
			try (Socket s = data()) {
				List<String> r = send("RETR " + name);
				assertEquals(150, TestUtf8AndOpts.code(r.get(0)), r.toString());
				ret = readAll(s.getInputStream());
			}
			List<String> done = read();
			assertEquals(226, TestUtf8AndOpts.code(done.get(0)), done.toString());
			return ret;
		}

		List<String> listing(String command) throws IOException {
			String text;
			try (Socket s = data()) {
				List<String> r = send(command);
				assertEquals(150, TestUtf8AndOpts.code(r.get(0)), r.toString());
				text = new String(readAll(s.getInputStream()), StandardCharsets.UTF_8);
			}
			List<String> done = read();
			assertEquals(226, TestUtf8AndOpts.code(done.get(0)), done.toString());
			List<String> lines = new ArrayList<>();
			for (String l : text.split("\r\n")) {
				if (!l.isEmpty()) {
					lines.add(l);
				}
			}
			return lines;
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

		@Override
		public void close() throws IOException {
			socket.close();
		}
	}
}
