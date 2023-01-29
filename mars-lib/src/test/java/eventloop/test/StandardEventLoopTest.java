package eventloop.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import eventloop.El;
import eventloop.StandardEventLoop;
import mars.utils.test.ElRunner;

class StandardEventLoopTest {
	final StandardEventLoop out = new StandardEventLoop();

	@Test
	void testDuringEventTimeIsStill() throws Exception {
		new ElRunner(out).runEventAndWait(() -> {
			final var t1 = El.now();
			Thread.sleep(1);
			final var t2 = El.now();
			assertEquals(t1, t2);
		});
	}

	@Test
	void testDifferentTimeInDifferentEvents() throws Exception {
		new ElRunner(out).runEventAndWait(() -> {
			final var t1 = El.now();
			final int delayMillis = 24;
			El.setTimeout(delayMillis, () -> {
				final var t2 = El.now();
				final long actual = t1.until(t2, ChronoUnit.MILLIS);
				assertTrue(actual >= delayMillis, actual + " is not >= " + delayMillis);
			});
		});
	}
}
