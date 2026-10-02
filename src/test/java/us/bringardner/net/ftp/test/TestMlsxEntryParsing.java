package us.bringardner.net.ftp.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import us.bringardner.net.ftp.client.FtpClientFile;

/**
 * MLSx entries (RFC 3659 section 7.2): facts, one space, the pathname (BJL-49). MLST sends
 * the whole pathname, which clients used to take as the file's name.
 */
public class TestMlsxEntryParsing {

	@Test
	public void splitsFactsFromThePathname() {
		assertArrayEquals(new String[] { "type=file;size=6;", "/pt/a.txt" },
				FtpClientFile.splitMlsxEntry(" type=file;size=6; /pt/a.txt"));
		assertArrayEquals(new String[] { "type=file;size=6;", "a b; c.txt" },
				FtpClientFile.splitMlsxEntry("type=file;size=6; a b; c.txt"), "names may hold spaces and ';'");
		assertArrayEquals(new String[] { "type=file;UNIX.owner=NT AUTHORITY\\SYSTEM;", "x.txt" },
				FtpClientFile.splitMlsxEntry("type=file;UNIX.owner=NT AUTHORITY\\SYSTEM; x.txt"), "fact values may hold spaces");
		assertNull(FtpClientFile.splitMlsxEntry("-rw-r--r-- 1 tony staff 6 Oct  1 12:25 a.txt"));
		assertNull(FtpClientFile.splitMlsxEntry(null));
	}

	@ParameterizedTest
	@CsvSource({ "a.txt, a.txt", "/pt/a.txt, a.txt", "/pt/dir/, dir", "/TestFiles, TestFiles", "'my file.txt', 'my file.txt'" })
	public void nameIsTheLastPart(String pathname, String name) {
		assertEquals(name, FtpClientFile.mlsxName(pathname));
	}
}
