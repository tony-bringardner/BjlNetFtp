package us.bringardner.net.ftp.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A tiny scripted FTP server for client tests that need behaviour the real server doesn't
 * have (no EPSV, dropping the control connection). Passive mode only, one data file.
 */
class FakeFtpServer implements AutoCloseable {

	final ServerSocket listener;
	/** Commands received, prefixed with the connection number: "1:USER anonymous" */
	final List<String> log = Collections.synchronizedList(new ArrayList<>());
	volatile boolean supportEpsv = true;
	/** Close connection #1 (without replying) when this command arrives, e.g. "PWD" */
	volatile String dropFirstConnectionOn = null;
	volatile byte[] data = "fake data".getBytes(StandardCharsets.UTF_8);
	private volatile boolean running = true;
	private int connections = 0;

	FakeFtpServer() throws IOException {
		listener = new ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"));
		Thread t = new Thread(this::acceptLoop, "FakeFtpServer");
		t.setDaemon(true);
		t.start();
	}

	int port() {
		return listener.getLocalPort();
	}

	private void acceptLoop() {
		while (running) {
			try {
				Socket s = listener.accept();
				int n;
				synchronized (this) {
					n = ++connections;
				}
				Thread t = new Thread(() -> session(s, n), "FakeFtpSession-" + n);
				t.setDaemon(true);
				t.start();
			} catch (IOException e) {
				return;
			}
		}
	}

	private void session(Socket s, int n) {
		String cwd = "/";
		ServerSocket pasv = null;
		try (Socket sock = s) {
			BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
			Writer out = new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8);
			reply(out, "220 fake ready");
			String line;
			while ((line = in.readLine()) != null) {
				log.add(n + ":" + line);
				String[] p = line.split(" ", 2);
				String cmd = p[0].toUpperCase();
				String arg = p.length > 1 ? p[1] : "";
				if (n == 1 && cmd.equals(dropFirstConnectionOn)) {
					return; // close without replying, like an idle timeout
				}
				switch (cmd) {
				case "USER": reply(out, "331 password please"); break;
				case "PASS": reply(out, "230 logged in"); break;
				case "TYPE": case "NOOP": reply(out, "200 ok"); break;
				case "FEAT": reply(out, "211 no features"); break;
				case "CWD": cwd = arg.startsWith("/") ? arg : (cwd.endsWith("/") ? cwd : cwd + "/") + arg; reply(out, "250 ok"); break;
				case "PWD": reply(out, "257 \"" + cwd + "\" is current directory"); break;
				case "EPSV":
					if (!supportEpsv) {
						reply(out, "500 unknown command");
						break;
					}
					pasv = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
					reply(out, "229 Entering Extended Passive Mode (|||" + pasv.getLocalPort() + "|)");
					break;
				case "PASV":
					pasv = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
					int port = pasv.getLocalPort();
					reply(out, "227 Entering Passive Mode (127,0,0,1," + (port / 256) + "," + (port % 256) + ")");
					break;
				case "RETR":
					if (pasv == null) {
						reply(out, "425 no data connection");
						break;
					}
					reply(out, "150 opening");
					try (ServerSocket l = pasv; Socket d = l.accept()) {
						OutputStream o = d.getOutputStream();
						o.write(data);
						o.flush();
					}
					pasv = null;
					reply(out, "226 done");
					break;
				case "QUIT": reply(out, "221 bye"); return;
				default: reply(out, "502 not implemented"); break;
				}
			}
		} catch (IOException e) {
			// session over
		} finally {
			if (pasv != null) {
				try {
					pasv.close();
				} catch (IOException e) {
				}
			}
		}
	}

	private static void reply(Writer out, String text) throws IOException {
		out.write(text + "\r\n");
		out.flush();
	}

	List<String> commands(int connection) {
		List<String> ret = new ArrayList<>();
		synchronized (log) {
			for (String l : log) {
				if (l.startsWith(connection + ":")) {
					ret.add(l.substring(l.indexOf(':') + 1));
				}
			}
		}
		return ret;
	}

	@Override
	public void close() throws IOException {
		running = false;
		listener.close();
	}
}
