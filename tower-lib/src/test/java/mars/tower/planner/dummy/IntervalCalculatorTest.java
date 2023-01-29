package mars.tower.planner.dummy;

import static org.joda.time.Duration.ZERO;
import static org.joda.time.Duration.standardMinutes;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collection;

import org.joda.time.Duration;
import org.joda.time.Instant;
import org.joda.time.Interval;
import org.junit.jupiter.api.Test;

class IntervalCalculatorTest {
	private final IntervalCalculator out = new IntervalCalculator();
	Instant t0 = Instant.parse("2020-01-01T12:00:00Z");

	@Test
	void testOneMinusOne() {
		final var actual = out.subtract(is(i(ZERO, standardMinutes(30))),
				is(i(standardMinutes(10), standardMinutes(20))));
		assertEquals(is(i(ZERO, standardMinutes(10)), i(standardMinutes(20), standardMinutes(30))), actual);
	}

	@Test
	void testOverlapsBefore() {
		final var two = is(iMin(0, 80));
		final var five = is(iMin(-20, 20));
		final var expected = is(iMin(20, 80));
		assertEquals(expected, out.subtract(two, five));
	}

	@Test
	void testOverlapsAfter() {
		final var two = is(iMin(0, 80));
		final var five = is(iMin(50, 120));
		final var expected = is(iMin(0, 50));
		assertEquals(expected, out.subtract(two, five));
	}

	@Test
	void testTwoMinusFive() {
		final var two = is(iMin(0, 80), iMin(110, 180));
		final var five = is(iMin(-20, 20), iMin(30, 50), iMin(70, 130), iMin(150, 160), iMin(170, 210));
		final var expected = is(iMin(20, 30), iMin(50, 70), iMin(130, 150), iMin(160, 170));
		assertEquals(expected, out.subtract(two, five));
	}

	@Test
	void testRemovesEmpty() {
		final var a = is(iMin(0, 80));
		final var b = is(iMin(0, 20));
		final var expected = is(iMin(20, 80));
		assertEquals(expected, out.subtract(a, b));
	}

	private Interval iMin(int s, int e) {
		return i(standardMinutes(s), standardMinutes(e));
	}

	private Interval i(Duration s, Duration e) {
		return new Interval(t0.plus(s), t0.plus(e));
	}

	private Collection<Interval> is(Interval... intervals) {
		return Arrays.asList(intervals);
	}

}
