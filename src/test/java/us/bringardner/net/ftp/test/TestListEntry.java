package us.bringardner.net.ftp.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import us.bringardner.net.ftp.client.ListEntry;

/**
 * ListEntry: the listing parser shared by FtpClientFile and bjl_file_system_ftp's FtpFile.
 */
public class TestListEntry {

	@Test
	public void testUnixLine() {
		ListEntry e = ListEntry.parse("-rw-r--r--   1 tony     staff        1234 Oct  1 12:25 my file.txt", false, null);
		assertEquals("my file.txt", e.getName(), "a name may contain spaces");
		assertEquals("tony", e.getOwner());
		assertEquals("staff", e.getGroup());
		assertEquals(1234, e.getLength());
		assertEquals(ListEntry.TYPE_FILE, e.getType());
		assertArrayEquals("rw-r--r--".toCharArray(), e.getPermissions());
		assertTrue(e.getLastModified() > 0);
		assertNull(e.getMlstPermissions());

		assertEquals(ListEntry.TYPE_DIR, ListEntry.parse("drwxr-xr-x 2 tony staff 64 Jan 15 2024 docs", false, null).getType());
	}

	@Test
	public void testNonAsciiOwner() {
		//  The old byte based cleanup used a UTF-8 byte index as a character index, so a non-ASCII
		//  owner or group garbled the name (bjl_file_system_ftp still had that copy)
		ListEntry e = ListEntry.parse("-rw-r--r--   1 jörg     grüppe        5 Oct  1 12:25 a.txt", false, null);
		assertEquals("jörg", e.getOwner());
		assertEquals("grüppe", e.getGroup());
		assertEquals("a.txt", e.getName());
		assertEquals(5, e.getLength());
	}

	@Test
	public void testMlsxLine() {
		ListEntry e = ListEntry.parse("type=file;size=42;modify=20260102030405;perm=adfrw; /dir/b c.txt", true, null);
		assertEquals("b c.txt", e.getName(), "MLST gives a path; the name is its last part");
		assertEquals(42, e.getLength());
		assertEquals(ListEntry.TYPE_FILE, e.getType());
		assertEquals("adfrw", e.getMlstPermissions());
		assertTrue(e.getLastModified() > 0);
		assertNull(e.getOwner());

		assertEquals(ListEntry.TYPE_DIR, ListEntry.parse("type=dir;size=0;modify=20260102030405; sub", true, null).getType());
	}

	@Test
	public void testNotReallyMlsxFallsBackToUnix() {
		ListEntry e = ListEntry.parse("-rw-r--r-- 1 tony staff 7 Oct  1 12:25 x.txt", true, null);
		assertEquals("x.txt", e.getName());
		assertEquals(7, e.getLength());
	}

	@Test
	public void testBadDateIsReported() {
		List<String> problems = new ArrayList<>();
		ListEntry e = ListEntry.parse("type=file;size=1;modify=not-a-time; a.txt", true, problems::add);
		assertEquals("a.txt", e.getName());
		assertEquals(0, e.getLastModified());
		assertEquals(1, problems.size(), problems.toString());
	}
}
