package us.bringardner.net.ftp.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

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
 * BJL-26: every command gets exactly one reply, and replies with several lines use the
 * RFC 959 multi-line form. Each case is followed by NOOP, which must get its own 200:
 * a second reply to the command would be read in its place.
 */
public class TestSiteReplies {

	private static final int PORT = 8039;
	private static FtpServer server;
	private static File base;

	@BeforeAll
	public static void startServer() throws Exception {
		base = new File("target/TestSiteReplies").getAbsoluteFile();
		File root = new File(base, "root");
		assertTrue(root.mkdirs() || root.isDirectory());
		assertTrue(new File(base, "other").mkdirs() || new File(base, "other").isDirectory());
		File acl = new File(base, "acl.txt");
		Files.write(acl.toPath(), (
				" #name , credentials, permissions\n"
				+ " admin , 0000 , ADMIN|SITE|READ|WRITE\n"
				+ " plain , 1234 , READ\n").getBytes(StandardCharsets.UTF_8));
		System.setProperty(FileBasedAcl.PROP_FILE_NAME, acl.getPath());
		System.setProperty(IServer.AUTHENTICATOION_PROVIDER_PROPERTY, FileBasedAcl.class.getCanonicalName());

		FileSource fs = FileSourceFactory.getDefaultFactory().createFileSource(root.getAbsolutePath());
		server = new FtpServer();
		server.setSecure(false);
		server.setFtpRoot(fs);
		server.setPort(PORT);
		server.setLoginFailureDelay(0);
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
		System.clearProperty(FileBasedAcl.PROP_FILE_NAME);
		System.clearProperty(IServer.AUTHENTICATOION_PROVIDER_PROPERTY);
		if (server != null) {
			server.stop();
			long start = System.currentTimeMillis();
			while (server.isRunning() && System.currentTimeMillis() - start < 10000) {
				Thread.sleep(10);
			}
		}
	}

	/** A raw control connection that returns every line of each reply. */
	private static final class Control implements AutoCloseable {
		final Socket socket = new Socket();
		final BufferedReader in;
		final Writer out;

		Control(String user, String password) throws IOException {
			socket.connect(new InetSocketAddress("127.0.0.1", PORT), 5000);
			socket.setSoTimeout(5000);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
			out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
			assertEquals(220, code(read())); // greeting (RFC 959)
			assertEquals(331, code(send("USER " + user)));
			assertEquals(230, code(send("PASS " + password)));
		}

		List<String> send(String command) throws IOException {
			out.write(command + "\r\n");
			out.flush();
			return read();
		}

		/** One reply: a single line, or "ddd-" ... "ddd " (RFC 959 4.2). Every line is checked. */
		List<String> read() throws IOException {
			List<String> lines = new ArrayList<>();
			String line = in.readLine();
			assertTrue(line != null, "connection closed");
			assertTrue(line.matches("\\d{3}[ -].*") || line.matches("\\d{3}"), "not a reply line: '" + line + "'");
			lines.add(line);
			if (line.length() > 3 && line.charAt(3) == '-') {
				String end = line.substring(0, 3) + " ";
				do {
					line = in.readLine();
					assertTrue(line != null, "connection closed inside a multi-line reply");
					lines.add(line);
				} while (!line.startsWith(end));
			}
			return lines;
		}

		/** The command's reply, then NOOP must get its own reply (no stray second reply). */
		List<String> sendOne(String command) throws IOException {
			List<String> reply = send(command);
			// NOOP answers "200 NOOP"; anything else is a leftover reply to the command
			assertEquals("200 NOOP", send("NOOP").get(0), "a second reply to '" + command + "' was waiting");
			return reply;
		}

		@Override
		public void close() throws IOException {
			socket.close();
		}
	}

	private static int code(List<String> reply) {
		return Integer.parseInt(reply.get(0).substring(0, 3));
	}

	@Test
	public void anUnknownSiteCommandGetsOneReply() throws Exception {
		try (Control c = new Control("admin", "0000")) {
			assertEquals(501, code(c.sendOne("SITE NOSUCH")));
		}
		try (Control c = new Control("plain", "1234")) {
			assertEquals(501, code(c.sendOne("SITE NOSUCH")));
		}
	}

	@Test
	public void siteRootNeedsAdmin() throws Exception {
		try (Control c = new Control("plain", "1234")) {
			assertEquals(534, code(c.sendOne("SITE root " + new File(base, "other").getPath())));
		}
	}

	@Test
	public void siteRootWithABadPathGetsOneReply() throws Exception {
		try (Control c = new Control("admin", "0000")) {
			assertEquals(501, code(c.sendOne("SITE root " + new File(base, "missing").getPath())));
		}
	}

	@Test
	public void siteRootChangesTheRoot() throws Exception {
		String other = new File(base, "other").getPath();
		try (Control c = new Control("admin", "0000")) {
			List<String> reply = c.sendOne("SITE root " + other);
			assertEquals(200, code(reply));
			assertEquals("200 Root is " + other, reply.get(0));
		}
	}

	@Test
	public void setFactoryReallyChangesTheFactory() throws Exception {
		try (Control c = new Control("admin", "0000")) {
			List<String> reply = c.sendOne("SITE SetFactory memory");
			assertEquals("200 FileSourceFactory =memory", reply.get(0));
			assertEquals(501, code(c.sendOne("SITE SetFactory nosuchfactory")));
		}
	}

	@Test
	public void textWithLineBreaksIsAMultiLineReply() throws Exception {
		try (Control c = new Control("plain", "1234")) {
			List<String> reply = c.sendOne("SITE modDate");
			assertEquals(2, reply.size(), reply.toString());
			assertTrue(reply.get(0).startsWith("501-"), reply.toString());
			assertTrue(reply.get(1).startsWith("501 USAGE"), reply.toString());

			reply = c.sendOne("HELP");
			assertEquals(2, reply.size(), reply.toString());
			assertTrue(reply.get(0).startsWith("501-") && reply.get(1).startsWith("501 "), reply.toString());

			reply = c.sendOne("SITE modDate notANumber x");
			assertEquals(1, reply.size(), reply.toString());
			assertFalse(reply.get(0).contains("Exception"), reply.toString());
		}
	}
}
