package us.bringardner.net.ftp.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import us.bringardner.core.ILogger.Level;
import us.bringardner.net.ftp.client.FtpClient;

/**
 * Active mode (PORT) against a server that behaves like vsftpd: it opens the data
 * connection only after it has accepted the transfer command (RFC 959).
 * <p>
 * The project's own FtpServer connects as soon as it receives PORT, which hid a client
 * bug: the client called accept() before sending RETR/STOR/LIST, so both ends waited for
 * each other until the transfer timeout.
 */
public class TestActiveModeOrdering {

	private static final String CONTENT = "hello from a lazy server";
	private static final String LISTING = "-rw-r--r-- 1 ftp ftp 24 Sep 30 12:00 file.txt";

	private ServerSocket control;
	private Thread serverThread;
	private final List<String> commands = new CopyOnWriteArrayList<>();
	private final StringBuilder uploaded = new StringBuilder();

	@BeforeEach
	public void startServer() throws IOException {
		control = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
		serverThread = new Thread(this::serve, "LazyActiveFtpServer");
		serverThread.setDaemon(true);
		serverThread.start();
	}

	@AfterEach
	public void stopServer() throws Exception {
		control.close();
		serverThread.join(5000);
	}

	@Test
	public void activeTransfersWhenServerConnectsAfterCommand() throws IOException {
		FtpClient client = new FtpClient(InetAddress.getLoopbackAddress().getHostAddress(), control.getLocalPort());
		client.getLogger().setLevel(Level.ERROR);
		client.setActive(true);
		// short, so the old ordering fails in seconds instead of hanging
		client.setTransferTimeout(3000);
		assertTrue(client.connect("user", "pass", null), "connect");
		try {
			try (InputStream in = client.getInputStream("file.txt")) {
				assertEquals(CONTENT, new String(in.readAllBytes(), StandardCharsets.UTF_8));
			}

			try (OutputStream out = client.getOutputStream("up.txt")) {
				out.write("uploaded".getBytes(StandardCharsets.UTF_8));
			}
			synchronized (uploaded) {
				assertEquals("uploaded", uploaded.toString());
			}

			String[] list = client.executeList();
			assertEquals(1, list.length, "listing");
			assertEquals(LISTING, list[0]);
		} finally {
			client.close();
		}
		assertTrue(commands.contains("PORT"), "the client used PORT: " + commands);
	}

	// ---- scripted server: replies to what the client sends, connects for data only after the command

	private void serve() {
		try (Socket s = control.accept();
				BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
				Writer out = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.US_ASCII)) {
			reply(out, "220 lazy server ready");
			String dataHost = null;
			int dataPort = -1;
			String line;
			while ((line = in.readLine()) != null) {
				int sp = line.indexOf(' ');
				String cmd = (sp < 0 ? line : line.substring(0, sp)).toUpperCase();
				String arg = sp < 0 ? "" : line.substring(sp + 1);
				commands.add(cmd);
				switch (cmd) {
				case "USER": reply(out, "331 password please"); break;
				case "PASS": reply(out, "230 logged in"); break;
				case "TYPE": reply(out, "200 type set"); break;
				case "PWD": reply(out, "257 \"/\""); break;
				case "PORT": {
					String[] p = arg.split(",");
					dataHost = p[0] + "." + p[1] + "." + p[2] + "." + p[3];
					dataPort = Integer.parseInt(p[4]) * 256 + Integer.parseInt(p[5]);
					reply(out, "200 PORT ok");
					break;
				}
				case "RETR":
				case "LIST":
				case "NLST":
				case "STOR":
					if (dataPort < 0) {
						reply(out, "425 use PORT first");
						break;
					}
					// like vsftpd: connect only now, after the command
					try (Socket data = new Socket(dataHost, dataPort)) {
						reply(out, "150 opening data connection");
						if (cmd.equals("STOR")) {
							byte[] b = data.getInputStream().readAllBytes();
							synchronized (uploaded) {
								uploaded.append(new String(b, StandardCharsets.UTF_8));
							}
						} else {
							String body = cmd.equals("RETR") ? CONTENT : LISTING + "\r\n";
							data.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
						}
					}
					dataPort = -1;
					reply(out, "226 transfer complete");
					break;
				case "QUIT":
					reply(out, "221 bye");
					return;
				default:
					reply(out, "502 not implemented");
				}
			}
		} catch (IOException e) {
			// client went away or the test closed the listener
		}
	}

	private static void reply(Writer out, String text) throws IOException {
		out.write(text + "\r\n");
		out.flush();
	}
}
