package mars.utils.test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eventloop.StandardEventLoop;
import eventloop.Timeout;

public class TestEventLoop extends StandardEventLoop {
	private static final Instant T0 = Instant.parse("2020-01-01T12:00:00Z");

	private final static Logger LOGGER = LoggerFactory.getLogger(TestEventLoop.class);

	private final List<TestTimeout> timeouts = new LinkedList<>();

	private Instant now = T0;

	private Instant timeLimit;

	@Override
	public Timeout setTimeout_(long millis, Event c) {
		if (millis < 0)
			throw new IllegalArgumentException("Timeout cannot be negative");
		LOGGER.debug("Setting timeout millis: {}, @{}", millis, now);
		final Instant time = now.plus(millis, ChronoUnit.MILLIS);
		if (time.isAfter(timeLimit))
			return Timeout.NULL;
		final TestTimeout timeout = new TestTimeout(time, c);
		final var index = timeouts.stream().filter(t -> !t.time.isAfter(time)).count();
		timeouts.add((int) index, timeout);
		super.setTimeout_(0, this::processTimeout);
		return timeout;
	}

	private void processTimeout() throws Throwable {
		LOGGER.debug("Processing timeout count: {}", timeouts.size());
		if (timeouts.isEmpty())
			return;
		final TestTimeout timeout = timeouts.remove(0);
		LOGGER.debug("Setting time @{}", timeout.time);
		this.now = timeout.time;
		try {
			timeout.event.run();
		} finally {
			if (timeouts.isEmpty()) {
				LOGGER.debug("timeouts empty");
				super.setTimeout_(0, this::processTimeout);
			}
		}
	}

	@Override
	public Instant now_() {
		return now;
	}

	private class TestTimeout implements Timeout {
		private final Instant time;
		private final Event event;

		public TestTimeout(Instant time, Event c) {
			this.time = time;
			this.event = c;
		}

		@Override
		public void cancel() {
			timeouts.remove(this);
		}
	}

	public Instant at(long amountToAdd, TemporalUnit unit) {
		return T0.plus(amountToAdd, unit);
	}

	public void setTimeLimit(long millis) throws InterruptedException {
		this.timeLimit = T0.plus(millis, ChronoUnit.MILLIS);
	}

	public Instant currentTime() {
		return now;
	}
}
