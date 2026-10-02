package us.bringardner.net.ftp.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import us.bringardner.core.ILogger.Level;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.net.framework.server.FileBasedAcl;
import us.bringardner.net.framework.server.IServer;
import us.bringardner.net.ftp.server.FtpServer;

/**
 * BJL-60: the framework runs and manages FTP transfer threads (Server.startTask) and the idle
 * watchdog runs on the server's scheduler: a transfer is stopped when its session ends or the
 * server stops, and no FTP thread is left after the server stops (the watchdog used to be a
 * static executor whose thread was never shut down).
 */
public class TestTransferThreads {

	private static final int PORT = 8045;
	private static final String NAME = "TransferThreadsFtp";
	private static final int SIZE = 8 * 1024 * 1024;

	private static FtpServer startServer() throws Exception {
		System.clearProperty(IServer.AUTHENTICATOION_PROVIDER_PROPERTY);
		System.clearProperty(FileBasedAcl.PROP_FILE_NAME);
		File root = new File("target/TestTransferThreads").getAbsoluteFile();
		root.mkdirs();
		File big = new File(root, "big.bin");
		if (big.length() != SIZE) {
			Files.write(big.toPath(), new byte[SIZE]);
		}
		FtpServer server = new FtpServer();
		server.setName(NAME);
		server.setSecure(false);
		server.setFtpRoot(FileSourceFactory.getDefaultFactory().createFileSource(root.getAbsolutePath()));
		server.setPort(PORT);
		server.getLogger().setLevel(Level.ERROR);
		server.start();
		waitFor(server::isRunning, "server did not start");
		return server;
	}

	private static void stopServer(FtpServer server) {
		server.stop();
		waitFor(() -> !server.isRunning(), "server did not stop");
	}

	private static void waitFor(BooleanSupplier condition, String message) {
		long end = System.currentTimeMillis() + 10000;
		while (!condition.getAsBoolean()) {
			assertTrue(System.currentTimeMillis() < end, message);
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private static boolean threadExists(String name) {
		for (Thread t : Thread.getAllStackTraces().keySet()) {
			if (t.isAlive() && t.getName().equals(name)) {
				return true;
			}
		}
		return false;
	}

	/** Start a RETR of the big file and leave it blocked (the client doesn't read). */
	private static Socket blockedDownload(Control c) throws IOException {
		assertEquals(200, code(c.send("TYPE I")));
		Socket d = c.epsv();
		String r = c.send("RETR big.bin");
		assertEquals(150, code(r), r);
		return d;
	}

	@Test
	public void transferIsStoppedWhenItsSessionEnds() throws Exception {
		FtpServer server = startServer();
		try {
			Socket data;
			try (Control c = new Control()) {
				data = blockedDownload(c);
				waitFor(() -> server.getTaskCount() == 1, "the transfer is not a server task");
			}
			// The control connection is closed: the server stops the transfer and closes its socket
			waitFor(() -> server.getTaskCount() == 0, "transfer still running after its session ended");
			data.setSoTimeout(10000);
			try (Socket d = data; InputStream in = d.getInputStream()) {
				byte[] buf = new byte[64 * 1024];
				long total = 0;
				int n;
				try {
					while ((n = in.read(buf)) > 0) {
						total += n;
					}
				} catch (IOException e) {
					// reset: also fine
				}
				assertTrue(total < SIZE, "the transfer should have been cut short, got " + total);
			}
		} finally {
			stopServer(server);
		}
	}

	@Test
	public void serverStopStopsTransfersAndLeavesNoThreads() throws Exception {
		FtpServer server = startServer();
		Control c = new Control();
		Socket data = blockedDownload(c);
		try {
			waitFor(() -> server.getTaskCount() == 1, "the transfer is not a server task");
			assertTrue(threadExists(NAME + "-scheduler"), "the watchdog runs on the server's scheduler");
			assertFalse(threadExists("FtpServerStream-watchdog"), "no static watchdog thread");
			stopServer(server);
			assertEquals(0, server.getTaskCount());
			waitFor(() -> !threadExists(NAME + "-scheduler"), "scheduler thread still running");
		} finally {
			data.close();
			c.close();
			if (server.isRunning()) {
				stopServer(server);
			}
		}
	}

	@Test
	public void completedTransferStillGets226() throws Exception {
		FtpServer server = startServer();
		try (Control c = new Control()) {
			assertEquals(200, code(c.send("TYPE I")));
			try (Socket d = c.epsv()) {
				String r = c.send("RETR big.bin");
				assertEquals(150, code(r), r);
				InputStream in = d.getInputStream();
				byte[] buf = new byte[64 * 1024];
				long total = 0;
				int n;
				while ((n = in.read(buf)) > 0) {
					total += n;
				}
				assertEquals(SIZE, total);
			}
			String done = c.read();
			assertEquals(226, code(done), done);
			waitFor(() -> server.getTaskCount() == 0, "finished transfer still counted");
		} finally {
			stopServer(server);
		}
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
			assertEquals(220, code(r), r);
			r = send("USER anonymous");
			if (code(r) == 331) {
				r = send("PASS guest");
			}
			assertEquals(230, code(r), r);
		}

		String send(String line) throws IOException {
			out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
			out.flush();
			return read();
		}

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
			assertTrue(code(r) == 229 && m.find(), r);
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
