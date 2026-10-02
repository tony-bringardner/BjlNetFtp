package us.bringardner.net.ftp.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import us.bringardner.core.ILogger.Level;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.net.framework.server.FileBasedAcl;
import us.bringardner.net.framework.server.IServer;
import us.bringardner.net.ftp.server.FtpServer;

/**
 * Starts the FTP server for the FTPS interoperability check with non-Java clients
 * (src/test/interop/ftps-interop.sh, BJL-2): explicit FTPS (AUTH TLS) on one port and
 * implicit FTPS on another, anonymous access, a self-signed test certificate.
 * <p>
 * Usage: InteropServer root explicitPort implicitPort [protocol]. Prints READY when both
 * servers listen and runs until standard input is closed.
 */
public class InteropServer {

	public static void main(String[] args) throws Exception {
		File root = new File(args[0]).getAbsoluteFile();
		int explicitPort = Integer.parseInt(args[1]);
		int implicitPort = Integer.parseInt(args[2]);
		if (args.length > 3) {
			System.setProperty("FtpServer.Protocol", args[3]);
		}
		root.mkdirs();
		File keystore = new File(TestFtpBaseTestClass.TEST_KEYSTORE);
		TestFtpBaseTestClass.makeTestKeystore(keystore);
		System.setProperty("FtpServer.KeyStoreName", keystore.getPath());
		System.setProperty("FtpServer.KeyStorePassword", TestFtpBaseTestClass.TEST_KEYSTORE_PASSWORD);
		System.setProperty("FtpServer.KeyStoreType", "PKCS12");
		System.setProperty("FtpServer.Algorithm", "SunX509");
		System.clearProperty(IServer.AUTHENTICATOION_PROVIDER_PROPERTY);
		System.clearProperty(FileBasedAcl.PROP_FILE_NAME);

		FtpServer explicit = start(root, explicitPort, false);
		FtpServer implicit = start(root, implicitPort, true);
		System.out.println("READY");
		System.out.flush();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
			while (in.readLine() != null) {
				// run until stdin closes
			}
		}
		explicit.stop();
		implicit.stop();
		System.exit(0);
	}

	private static FtpServer start(File root, int port, boolean implicit) throws Exception {
		FtpServer s = new FtpServer();
		s.setSecure(implicit);
		s.setFtpRoot(FileSourceFactory.getDefaultFactory().createFileSource(root.getAbsolutePath()));
		s.setPort(port);
		s.getLogger().setLevel(Level.ERROR);
		s.start();
		long t = System.currentTimeMillis();
		while (!s.isRunning()) {
			if (System.currentTimeMillis() - t > 10000) {
				throw new IllegalStateException("server on port " + port + " did not start");
			}
			Thread.sleep(20);
		}
		return s;
	}
}
