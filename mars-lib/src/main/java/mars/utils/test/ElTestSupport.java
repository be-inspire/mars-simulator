package mars.utils.test;

import java.time.Instant;
import java.time.temporal.TemporalUnit;

import eventloop.EventLoop.Event;

public class ElTestSupport {

	private final TestEventLoop eventLoop;
	private final ElRunner runner;

	public ElTestSupport() {
		this.eventLoop = new TestEventLoop();
		runner = new ElRunner(eventLoop);
	}

	public void runEventAndWait(long millis, Event event) throws Exception {
		eventLoop.setTimeLimit(millis);
		runner.runEventAndWait(event);
	}

	public Instant currentTime() {
		return eventLoop.currentTime();
	}

	public Instant at(long amountToAdd, TemporalUnit unit) {
		return eventLoop.at(amountToAdd, unit);
	}
}
