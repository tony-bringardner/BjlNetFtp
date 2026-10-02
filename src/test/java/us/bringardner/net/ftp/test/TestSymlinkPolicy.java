package us.bringardner.net.ftp.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.core.ILogger.Level;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.net.framework.server.FileBasedAcl;
import us.bringardner.net.framework.server.IServer;
import us.bringardner.net.ftp.server.FtpServer;
import us.bringardner.net.ftp.server.FtpServer.SymlinkPolicy;
import us.bringardner.net.ftp.test.TestTransferReliability.Session;

/**
 * The three symbolic link policies. Layout:
 * <pre>
 * base/root/inside.txt
 * base/root/out     -> base/outside   (not allowed)
 * base/root/shared  -> base/shared    (allowed target)
 * base/outside/secret.txt
 * base/shared/doc.txt
 * </pre>
 */
public class TestSymlinkPolicy {

	private static final int PORT = 8036;
	private static FtpServer server;
	private static File base, root, outside, shared;

	@BeforeAll
	public static void start() throws Exception {
		System.clearProperty(IServer.AUTHENTICATOION_PROVIDER_PROPERTY);
		System.clearProperty(FileBasedAcl.PROP_FILE_NAME);
		base = new File("target/FtpSymlinkPolicy").getAbsoluteFile();
		deleteAll(base);
		root = new File(base, "root");
		outside = new File(base, "outside");
		shared = new File(base, "shared");
		assertTrue(root.mkdirs() && outside.mkdirs() && shared.mkdirs());
		Files.write(new File(root, "inside.txt").toPath(), "inside".getBytes(StandardCharsets.UTF_8));
		Files.write(new File(outside, "secret.txt").toPath(), "secret".getBytes(StandardCharsets.UTF_8));
		Files.write(new File(shared, "doc.txt").toPath(), "shared doc".getBytes(StandardCharsets.UTF_8));
		try {
			Files.createSymbolicLink(new File(root, "out").toPath(), outside.toPath());
			Files.createSymbolicLink(new File(root, "shared").toPath(), shared.toPath());
		} catch (UnsupportedOperationException | IOException e) {
			Assumptions.assumeTrue(false, "symbolic links not supported: " + e);
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

	@AfterEach
	public void resetPolicy() {
		server.setSymlinkPolicy(SymlinkPolicy.STRICT);
		server.setAllowedLinkTargets(Collections.emptyList());
	}

	@AfterAll
	public static void stop() throws Exception {
		if (server != null) {
			server.stop();
		}
		deleteAll(base);
	}

	@Test
	public void defaultIsStrict() {
		assertEquals(SymlinkPolicy.STRICT, new FtpServer().getSymlinkPolicy());
	}

	@Test
	public void parseAcceptsTheDocumentedNames() {
		assertEquals(SymlinkPolicy.STRICT, SymlinkPolicy.parse("strict"));
		assertEquals(SymlinkPolicy.ALLOWED_TARGETS, SymlinkPolicy.parse("allowedTargets"));
		assertEquals(SymlinkPolicy.ALLOWED_TARGETS, SymlinkPolicy.parse("ALLOWED_TARGETS"));
		assertEquals(SymlinkPolicy.FOLLOW, SymlinkPolicy.parse(" Follow "));
		assertThrows(IllegalArgumentException.class, () -> SymlinkPolicy.parse("yes"));
	}

	@Test
	public void strictRefusesEveryLinkOut() throws Exception {
		try (Session s = new Session(PORT)) {
			assertEquals(213, size(s, "inside.txt"));
			assertEquals(550, size(s, "out/secret.txt"));
			assertEquals(550, size(s, "shared/doc.txt"));
			s.send("CWD shared");
			assertTrue(s.read(5000).code >= 400);
			assertEquals("/", pwd(s));
		}
	}

	@Test
	public void allowedTargetsFollowsOnlyListedDirectories() throws Exception {
		server.setSymlinkPolicy(SymlinkPolicy.ALLOWED_TARGETS);
		server.setAllowedLinkTargets(Arrays.asList(shared.getAbsolutePath()));
		try (Session s = new Session(PORT)) {
			assertEquals(213, size(s, "shared/doc.txt"));
			assertEquals(550, size(s, "out/secret.txt"));
			assertEquals("shared doc", new String(s.get("shared/doc.txt"), StandardCharsets.UTF_8));

			s.send("CWD shared");
			assertEquals(250, s.read(5000).code);
			assertEquals("/shared", pwd(s), "PWD shows the link's name, not the real path");
			assertEquals(213, size(s, "doc.txt"));
			// ".." goes back up the virtual path, not to the link target's real parent
			assertEquals(550, size(s, "../outside/secret.txt"));
			assertEquals(550, size(s, "../../outside/secret.txt"));
			s.send("CDUP");
			assertEquals(250, s.read(5000).code);
			assertEquals("/", pwd(s));
		}
	}

	@Test
	public void followFollowsEveryLinkButNotDotDot() throws Exception {
		server.setSymlinkPolicy(SymlinkPolicy.FOLLOW);
		try (Session s = new Session(PORT)) {
			assertEquals(213, size(s, "out/secret.txt"));
			assertEquals(213, size(s, "shared/doc.txt"));
			assertEquals(550, size(s, "../outside/secret.txt"), ".. still can't leave the root");

			s.send("CWD out");
			assertEquals(250, s.read(5000).code);
			String pwd = pwd(s);
			assertEquals("/out", pwd, "PWD must not reveal the server's real path");
			s.send("CWD ..");
			assertEquals(250, s.read(5000).code);
			assertEquals("/", pwd(s));
		}
	}

	/** MLSD entries by name (BJL-32 replaced per-entry lookups; the results must not change) */
	private static java.util.Map<String, String> mlsd(Session s, String path) throws IOException {
		java.util.Map<String, String> ret = new java.util.TreeMap<>();
		String text;
		try (java.net.Socket data = s.pasv()) {
			s.send("MLSD " + path);
			s.expectPreliminary();
			text = new String(data.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		}
		s.expectComplete();
		for (String line : text.split("\r\n")) {
			int sp = line.indexOf(' ');
			if (sp > 0) {
				ret.put(line.substring(sp + 1), line.substring(0, sp).toLowerCase());
			}
		}
		return ret;
	}

	private static String fact(String facts, String name) {
		for (String f : facts.split(";")) {
			if (f.startsWith(name + "=")) {
				return f.substring(name.length() + 1);
			}
		}
		return null;
	}

	@Test
	public void mlsdPermissionsFollowThePolicy() throws Exception {
		try (Session s = new Session(PORT)) {
			java.util.Map<String, String> strict = mlsd(s, "/");
			assertTrue(!fact(strict.get("inside.txt"), "perm").isEmpty(), strict.toString());
			assertEquals("", fact(strict.get("out"), "perm"), "a link out gets no permissions: " + strict);
			assertEquals("", fact(strict.get("shared"), "perm"), strict.toString());
		}
		server.setSymlinkPolicy(SymlinkPolicy.ALLOWED_TARGETS);
		server.setAllowedLinkTargets(Arrays.asList(shared.getAbsolutePath()));
		try (Session s = new Session(PORT)) {
			java.util.Map<String, String> allowed = mlsd(s, "/");
			assertTrue(!fact(allowed.get("shared"), "perm").isEmpty(), allowed.toString());
			assertEquals("", fact(allowed.get("out"), "perm"), allowed.toString());
		}
	}

	/** The allowed targets are cached per session, but a change on the server takes effect. */
	@Test
	public void allowedTargetsChangesApplyToOpenSessions() throws Exception {
		server.setSymlinkPolicy(SymlinkPolicy.ALLOWED_TARGETS);
		try (Session s = new Session(PORT)) {
			assertEquals(550, size(s, "shared/doc.txt"));
			server.setAllowedLinkTargets(Arrays.asList(shared.getAbsolutePath()));
			assertEquals(213, size(s, "shared/doc.txt"));
			server.setAllowedLinkTargets(Collections.emptyList());
			assertEquals(550, size(s, "shared/doc.txt"));
		}
	}

	/** type=cdir / pdir / dir, now worked out from paths (BJL-32) */
	@Test
	public void mlsdDirectoryTypes() throws Exception {
		assertTrue(new File(root, "a/b").mkdirs() || new File(root, "a/b").isDirectory());
		assertTrue(new File(root, "a/c").mkdirs() || new File(root, "a/c").isDirectory());
		try (Session s = new Session(PORT)) {
			s.send("CWD a/b");
			assertEquals(250, s.read(5000).code);
			assertEquals("pdir", fact(mlsd(s, "/").get("a"), "type"), "an ancestor of the current directory");
			java.util.Map<String, String> a = mlsd(s, "/a");
			assertEquals("cdir", fact(a.get("b"), "type"), "the current directory");
			assertEquals("dir", fact(a.get("c"), "type"));
			assertEquals("file", fact(mlsd(s, "/").get("inside.txt"), "type"));
		}
	}

	private static int size(Session s, String path) throws IOException {
		s.send("SIZE " + path);
		return s.read(5000).code;
	}

	private static String pwd(Session s) throws IOException {
		s.send("PWD");
		String text = s.expect(257).text;
		int a = text.indexOf('"');
		int b = text.indexOf('"', a + 1);
		return text.substring(a + 1, b);
	}

	private static void deleteAll(File f) {
		if (f == null || (!f.exists() && !Files.isSymbolicLink(f.toPath()))) {
			return;
		}
		if (!Files.isSymbolicLink(f.toPath())) {
			File[] kids = f.listFiles();
			if (kids != null) {
				for (File k : kids) {
					deleteAll(k);
				}
			}
		}
		f.delete();
	}
}
