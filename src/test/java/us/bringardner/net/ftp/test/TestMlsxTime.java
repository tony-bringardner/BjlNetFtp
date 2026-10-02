package us.bringardner.net.ftp.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import us.bringardner.net.ftp.server.commands.Mlst;

/**
 * MLSx modify= / MDTM times (BJL-36): written as YYYYMMDDHHMMSS.sss with an immutable
 * DateTimeFormatter, read back as any RFC 3659 time-val (fraction optional, any length).
 */
public class TestMlsxTime {

	private static long millis(int y, int mo, int d, int h, int mi, int s, int ms) {
		return LocalDateTime.of(y, mo, d, h, mi, s, ms * 1_000_000).atZone(Mlst.timeZone()).toInstant().toEpochMilli();
	}

	@Test
	public void formatsWithMilliseconds() {
		assertEquals("20261001203612.345", Mlst.formatTime(millis(2026, 10, 1, 20, 36, 12, 345)));
		assertEquals("19991231235959.007", Mlst.formatTime(millis(1999, 12, 31, 23, 59, 59, 7)));
		assertEquals("20240229000000.000", Mlst.formatTime(millis(2024, 2, 29, 0, 0, 0, 0)));
	}

	@ParameterizedTest
	@CsvSource({
		"20261001203612,          0",
		"20261001203612.3,      300",
		"20261001203612.34,     340",
		"20261001203612.345,    345",
		"20261001203612.345678, 345",
		"' 20261001203612.345 ', 345" })
	public void parsesAnyFractionLength(String value, int ms) {
		assertEquals(millis(2026, 10, 1, 20, 36, 12, ms), Mlst.parseTime(value));
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026100120361", "20261301203612", "20261001253612", "20261001203612.", "20261001203612.abc", "yesterday", "" })
	public void rejectsBadTimes(String value) {
		assertThrows(DateTimeException.class, () -> Mlst.parseTime(value));
	}

	@Test
	public void roundTrips() {
		for (long t = millis(2020, 1, 1, 0, 0, 0, 0); t < millis(2027, 1, 1, 0, 0, 0, 0); t += 3_333_333_337L) {
			assertEquals(t, Mlst.parseTime(Mlst.formatTime(t)), Mlst.formatTime(t));
		}
	}

	@Test
	public void concurrentFormattingIsConsistent() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			List<Future<Integer>> results = new ArrayList<>();
			for (int t = 0; t < 8; t++) {
				final long base = millis(2026, 1, 1, 0, 0, 0, 0) + t * 86_400_000L;
				results.add(pool.submit(() -> {
					int wrong = 0;
					for (int i = 0; i < 5000; i++) {
						long v = base + i * 1_001L;
						if (Mlst.parseTime(Mlst.formatTime(v)) != v) {
							wrong++;
						}
					}
					return wrong;
				}));
			}
			for (Future<Integer> f : results) {
				assertEquals(0, f.get(30, TimeUnit.SECONDS).intValue());
			}
		} finally {
			pool.shutdownNow();
		}
	}
}
