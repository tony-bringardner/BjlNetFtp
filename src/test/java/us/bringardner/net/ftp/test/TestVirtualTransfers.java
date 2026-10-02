package us.bringardner.net.ftp.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.core.BaseThread;
import us.bringardner.core.ILogger.Level;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.net.framework.server.FileBasedAcl;
import us.bringardner.net.framework.server.IServer;
import us.bringardner.net.framework.server.Server.VirtualThreads;
import us.bringardner.net.ftp.server.FtpServer;

/**
 * BJL-52: with Server VirtualThreads ON, a data transfer runs on a virtual thread like its
 * session, so many sessions with a transfer each need few platform threads. The transfers are
 * left blocked (the client doesn't read) while the threads are counted, which is also the case
 * that pinned carrier threads on Java 21-23 while transfer and reply code was synchronized.
 */
public class TestVirtualTransfers {

	private static final int PORT = 8044;
	private static final int SESSIONS = 60;
	private static final int SIZE = 4 * 1024 * 1024;
	private static FtpServer server;
	private static byte[] content;

	@BeforeAll
	public static void start() throws Exception {
		System.clearProperty(IServer.AUTHENTICATOION_PROVIDER_PROPERTY);
		System.clearProperty(FileBasedAcl.PROP_FILE_NAME);
		File root = new File("target/TestVirtualTransfers").getAbsoluteFile();
		root.mkdirs();
		content = new byte[SIZE];
		new Random(52).nextBytes(content);
		Files.write(new File(root, "big.bin").toPath(), content);
		server = new FtpServer();
		server.setSecure(false);
		server.setFtpRoot(FileSourceFactory.getDefaultFactory().createFileSource(root.getAbsolutePath()));
		server.setPort(PORT);
		server.setVirtualThreads(VirtualThreads.ON);
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
	public void manyBlockedTransfersFewPlatformThreads() throws Exception {
		ThreadMXBean mx = ManagementFactory.getThreadMXBean();
		int before = mx.getThreadCount();
		List<Control> sessions = new ArrayList<>();
		List<Socket> data = new ArrayList<>();
		try {
			for (int i = 0; i < SESSIONS; i++) {
				Control c = new Control();
				sessions.add(c);
				assertEquals(200, c.code("TYPE I"));
				Socket d = c.epsv();
				data.add(d);
				String r = c.send("RETR big.bin");
				assertEquals(150, code(r), r);
				// read a little: the transfer has started and is now blocked writing
				assertEquals(content[0], (byte) d.getInputStream().read());
			}
			// SESSIONS sessions + SESSIONS transfers
			int added = mx.getThreadCount() - before;
			if (BaseThread.isVirtualSupported()) {
				assertTrue(added < SESSIONS / 2, (2 * SESSIONS) + " session/transfer threads added " + added + " platform threads");
			} else {
				assertTrue(added >= 2 * SESSIONS, "a platform thread each on this JDK, added " + added);
			}
			// The control connections still answer while the transfers are blocked
			for (Control c : sessions) {
				assertEquals(200, c.code("NOOP"));
			}
			for (int i = 0; i < SESSIONS; i++) {
				byte[] got;
				try (Socket d = data.get(i)) {
					got = readAll(d.getInputStream(), SIZE - 1);
				}
				assertEquals(SIZE - 1, got.length);
				assertEquals(content[SIZE - 1], got[got.length - 1]);
				String r = sessions.get(i).read();
				assertEquals(226, code(r), r);
			}
		} finally {
			for (Socket d : data) {
				d.close();
			}
			for (Control c : sessions) {
				c.close();
			}
		}
	}

	private static byte[] readAll(InputStream in, int expected) throws IOException {
		byte[] ret = new byte[expected];
		int off = 0;
		int n;
		while (off < expected && (n = in.read(ret, off, expected - off)) > 0) {
			off += n;
		}
		return off == expected ? ret : java.util.Arrays.copyOf(ret, off);
	}

	private static int code(String line) {
		return Integer.parseInt(line.substring(0, 3));
	}

	/** A raw control connection, logged in as anonymous. */
	private static final class Control implements AutoCloseable {
		private static final Pattern EPSV = Pattern.compile("\\(\\|\\|\\|(\\d+)\\|\\)");
		private final Socket socket;
		private final BufferedReader in;
		private final OutputStream out;

		Control() throws IOException {
			socket = new Socket(InetAddress.getLoopbackAddress(), PORT);
			socket.setSoTimeout(20000);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
			out = socket.getOutputStream();
			String r = read();
			assertEquals(220, TestVirtualTransfers.code(r), r);
			r = send("USER anonymous");
			if (TestVirtualTransfers.code(r) == 331) {
				r = send("PASS guest");
			}
			assertEquals(230, TestVirtualTransfers.code(r), r);
		}

		String send(String line) throws IOException {
			out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
			out.flush();
			return read();
		}

		int code(String line) throws IOException {
			return TestVirtualTransfers.code(send(line));
		}

		/** One reply; returns the last line of a multi-line reply. */
		String read() throws IOException {
			String line = in.readLine();
			assertTrue(line != null, "control connection closed");
			if (line.length() > 3 && line.charAt(3) == '-') {
				String end = line.substring(0, 3) + " ";
				do {
					line = in.readLine();
					assertTrue(line != null, "closed inside a multi-line reply");
				} while (!line.startsWith(end));
			}
			return line;
		}

		Socket epsv() throws IOException {
			String r = send("EPSV");
			Matcher m = EPSV.matcher(r);
			assertTrue(TestVirtualTransfers.code(r) == 229 && m.find(), r);
			Socket s = new Socket(InetAddress.getLoopbackAddress(), Integer.parseInt(m.group(1)));
			s.setSoTimeout(20000);
			return s;
		}

		@Override
		public void close() throws IOException {
			socket.close();
		}
	}
}
