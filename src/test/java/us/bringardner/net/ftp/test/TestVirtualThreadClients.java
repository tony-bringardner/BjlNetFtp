package us.bringardner.net.ftp.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import us.bringardner.net.ftp.client.FtpClient;

/**
 * BJL-58: many FtpClients used from virtual threads, each waiting for a slow reply, must not
 * hold all the carrier threads. With synchronized FtpClient methods, on Java 21-23 each waiting
 * command pinned a carrier thread, so with more waiting clients than CPUs no other virtual
 * thread could run. Needs Java 21+ (skipped before); compiled for Java 11, so the virtual
 * thread executor is created by reflection.
 */
public class TestVirtualThreadClients {

	private static final int CLIENTS = Math.max(32, Runtime.getRuntime().availableProcessors() * 4);

	@Test
	public void waitingClientsDontStarveOtherVirtualThreads() throws Exception {
		Assumptions.assumeTrue(Runtime.version().feature() >= 21, "needs virtual threads (Java 21+)");
		ExecutorService exec = (ExecutorService) Executors.class.getMethod("newVirtualThreadPerTaskExecutor").invoke(null);
		List<FtpClient> clients = new ArrayList<>();
		CountDownLatch hold = new CountDownLatch(1);
		try (FakeFtpServer fake = new FakeFtpServer()) {
			for (int i = 0; i < CLIENTS; i++) {
				FtpClient c = new FtpClient("127.0.0.1", fake.port());
				c.setCmdTimeout(30000);
				assertTrue(c.connect("anonymous", "x", null));
				clients.add(c);
			}
			fake.holdNoop = hold;
			List<Future<Integer>> replies = new ArrayList<>();
			for (FtpClient c : clients) {
				replies.add(exec.submit(() -> c.executeCommand("NOOP")._getResponseCode()));
			}
			// Let every client send its NOOP and wait for the reply
			Thread.sleep(500);

			Future<String> other = exec.submit(() -> "ran");
			assertEquals("ran", other.get(5, TimeUnit.SECONDS), "a virtual thread could not run: carriers pinned");

			hold.countDown();
			for (Future<Integer> r : replies) {
				assertEquals(200, r.get(10, TimeUnit.SECONDS).intValue());
			}
		} finally {
			hold.countDown();
			for (FtpClient c : clients) {
				c.close();
			}
			exec.shutdownNow();
		}
	}
}
