package us.bringardner.net.ftp.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLContext;

import org.junit.jupiter.api.Test;

import us.bringardner.net.framework.Connection;
import us.bringardner.net.ftp.server.FtpRequestProcessor;
import us.bringardner.net.ftp.server.commands.Feat;

/**
 * Multi-line replies go out with one write and one flush (BJL-35): FEAT, MLST and any
 * reply with line breaks used to be one write (TCP segment, TLS record) per line.
 */
public class TestMultiLineReplies {

	private static final class CountingOut extends OutputStream {
		final ByteArrayOutputStream data = new ByteArrayOutputStream();
		int writes;

		@Override
		public void write(int b) {
			writes++;
			data.write(b);
		}

		@Override
		public void write(byte[] b, int off, int len) {
			writes++;
			data.write(b, off, len);
		}

		String text() {
			return new String(data.toByteArray(), StandardCharsets.UTF_8);
		}
	}

	private static CountingOut attach(FtpRequestProcessor p) throws IOException {
		CountingOut out = new CountingOut();
		Socket fake = new Socket() {
			@Override
			public OutputStream getOutputStream() {
				return out;
			}

			@Override
			public InputStream getInputStream() {
				return new ByteArrayInputStream(new byte[0]);
			}
		};
		p.setConnection(new Connection(fake, true) {
			@Override
			public SSLContext getSSLContext(String sslOrTsl) {
				return null;
			}
		});
		return out;
	}

	@Test
	public void multiLineTextIsOneWrite() throws Exception {
		FtpRequestProcessor p = new FtpRequestProcessor();
		CountingOut out = attach(p);
		p.reply(214, "Commands:\nUSER PASS\n200 looks like a code\nEnd");
		assertEquals(1, out.writes, "writes for a 4 line reply");
		assertEquals("214-Commands:\r\nUSER PASS\r\n 200 looks like a code\r\n214 End\r\n", out.text(),
				"RFC 959 multi-line format is unchanged (BJL-26)");
	}

	@Test
	public void featIsOneWrite() throws Exception {
		FtpRequestProcessor p = new FtpRequestProcessor();
		CountingOut out = attach(p);
		Feat feat = (Feat) ((us.bringardner.net.ftp.server.FtpCommandFactory) p.getCommandFactory()).getCommand("FEAT");
		feat.execute(p, new us.bringardner.net.framework.server.DefaultRequestContext("FEAT"));
		String text = out.text();
		assertEquals(1, out.writes, "writes for FEAT: " + text);
		String[] lines = text.split("\r\n");
		assertTrue(lines.length > 3, text);
		assertEquals("211-Extensions supported:", lines[0]);
		assertEquals("211 End", lines[lines.length - 1]);
		for (int i = 1; i < lines.length - 1; i++) {
			assertTrue(lines[i].startsWith(" "), "RFC 2389: feature lines start with a space: " + lines[i]);
		}
	}

	@Test
	public void singleLineRepliesAreUnchanged() throws Exception {
		FtpRequestProcessor p = new FtpRequestProcessor();
		CountingOut out = attach(p);
		p.reply(200, "OK");
		p.reply(200, "OK again");
		assertEquals(2, out.writes);
		assertEquals("200 OK\r\n200 OK again\r\n", out.text());
	}
}
