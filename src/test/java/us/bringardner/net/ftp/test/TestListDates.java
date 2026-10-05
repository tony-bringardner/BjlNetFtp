package us.bringardner.net.ftp.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import us.bringardner.io.filesource.FileSource;
import us.bringardner.io.filesource.FileSourceFactory;
import us.bringardner.net.ftp.client.FtpClientFile;
import us.bringardner.net.ftp.client.ListEntry;
import us.bringardner.net.ftp.server.commands.List;

/**
 * LIST dates (BJL-45): written like ls ("Oct  1 12:25", "Dec 14  2024") and read back by
 * FtpClientFile with the right year.
 * <p>
 * The day used to be zero padded ("Oct 01"), and the client parsed recent entries (time, no
 * year) as year 12 (the hour read as the year).
 */
public class TestListDates {

	private static final ZoneId NY = ZoneId.of("America/New_York");
	/** 2026-10-01 10:00 in New York */
	private static final long NOW = millis(2026, 10, 1, 10, 0);

	private static long millis(int y, int mo, int d, int h, int mi) {
		return LocalDateTime.of(y, mo, d, h, mi).atZone(NY).toInstant().toEpochMilli();
	}

	@ParameterizedTest
	@CsvSource({
		"2026, 10,  1,  8,  5, 'Oct  1 08:05'",
		"2026,  9, 15, 23, 59, 'Sep 15 23:59'",
		"2026,  4,  2, 10,  0, 'Apr  2 10:00'",   // just under six months
		"2026,  3, 31, 10,  0, 'Mar 31  2026'",   // just over six months
		"2025, 12, 31, 12,  0, 'Dec 31  2025'",
		"2026, 10,  1, 11,  0, 'Oct  1  2026'",   // in the future
		"2024, 12, 14, 12, 25, 'Dec 14  2024'",
		"2020,  2,  9,  1,  2, 'Feb  9  2020'" })
	public void formatsLikeLs(int y, int mo, int d, int h, int mi, String expected) {
		assertEquals(expected, List.formatListDate(millis(y, mo, d, h, mi), NOW, NY));
	}

	@Test
	public void monthNamesAreEnglishWhateverTheLocale() {
		java.util.Locale before = java.util.Locale.getDefault();
		try {
			java.util.Locale.setDefault(java.util.Locale.GERMANY);
			assertEquals("Jul  3 10:00", List.formatListDate(millis(2026, 7, 3, 10, 0), NOW, NY));
			assertEquals("Mar  3  2026", List.formatListDate(millis(2026, 3, 3, 10, 0), NOW, NY));
		} finally {
			java.util.Locale.setDefault(before);
		}
	}

	@ParameterizedTest
	@CsvSource({
		// recent entries get the year that puts them in the past
		"Oct,  1, 08:05, 2026, 10,  1,  8,  5",
		"Oct, 01, 08:05, 2026, 10,  1,  8,  5",
		"Oct,  1, 23:30, 2026, 10,  1, 23, 30",   // later today: within a day, still this year
		"Oct,  3, 12:00, 2025, 10,  3, 12,  0",   // more than a day ahead: last year
		"Dec, 14, 12:25, 2025, 12, 14, 12, 25",
		"Jan,  2,  9:07, 2026,  1,  2,  9,  7",
		"Feb, 29, 10:00, 2024,  2, 29, 10,  0",   // no Feb 29 in 2026 or 2025
		// older entries carry the year
		"Dec, 14, 2024, 2024, 12, 14,  0,  0",
		"jan,  5, 1999, 1999,  1,  5,  0,  0" })
	public void parsesLikeAClient(String mon, String day, String timeOrYear, int y, int mo, int d, int h, int mi) {
		assertEquals(millis(y, mo, d, h, mi), List.parseListDate(mon, day, timeOrYear, NY, NOW));
	}

	@ParameterizedTest
	@CsvSource({ "Foo, 1, 12:00", "Oct, x, 12:00", "Oct, 32, 2024", "Oct, 1, 25:00", "Oct, 1, abcd" })
	public void rejectsBadDates(String mon, String day, String timeOrYear) {
		assertThrows(DateTimeException.class, () -> List.parseListDate(mon, day, timeOrYear, NY, NOW));
	}

	@Test
	public void roundTripsThroughFormatAndParse() {
		for (long t = NOW - 400L * 24 * 3600 * 1000; t < NOW; t += 7L * 3600 * 1000 + 13 * 60 * 1000) {
			String[] p = List.formatListDate(t, NOW, NY).trim().split("\\s+");
			long back = List.parseListDate(p[0], p[1], p[2], NY, NOW);
			if (p[2].indexOf(':') > 0) {
				assertEquals(t / 60000 * 60000, back, "recent " + p[0] + " " + p[1] + " " + p[2]);
			} else {
				LocalDateTime lt = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(t), NY);
				assertEquals(lt.toLocalDate().atStartOfDay(NY).toInstant().toEpochMilli(), back, "old " + p[0] + " " + p[1] + " " + p[2]);
			}
		}
	}

	@Test
	public void serverEntryForAFileModifiedOnTheFirst() throws Exception {
		File f = File.createTempFile("listdate", ".txt");
		try {
			Files.write(f.toPath(), "x".getBytes());
			// the latest 1st of a month at 00:05 that is already past (a future time shows the year)
			LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
			LocalDateTime first = now.withDayOfMonth(1).withHour(0).withMinute(5).withSecond(0).withNano(0);
			if (first.isAfter(now)) {
				first = first.minusMonths(1);
			}
			long t = first.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
			assertTrue(f.setLastModified(t));
			FileSource fs = FileSourceFactory.getDefaultFactory().createFileSource(f.getAbsolutePath());
			String entry = new List().formatFile(fs);
			String expected = List.RECENT_FORMAT.format(first);
			assertTrue(entry.contains(" " + expected + " "), entry);
			assertTrue(expected.matches("[A-Z][a-z]{2}  1 00:05"), expected);
		} finally {
			f.delete();
		}
	}

	@Test
	public void clientReadsRecentEntriesWithTheRightYear() throws Exception {
		LocalDateTime recent = LocalDateTime.now(ZoneId.systemDefault()).minusDays(3).withSecond(0).withNano(0);
		String entry = "-rw-r--r--   1 tony  staff                       1330 " + List.RECENT_FORMAT.format(recent) + " a.txt";
		//  The client's LIST parsing (shared with bjl_file_system_ftp) is in ListEntry
		ListEntry f = ListEntry.parse(entry, false, null);
		assertEquals(recent.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), f.getLastModified(), entry);
		assertEquals("a.txt", f.getName());
	}
}
