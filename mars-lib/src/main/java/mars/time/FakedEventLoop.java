package mars.time;

import java.time.Instant;

import eventloop.StandardEventLoop;
import eventloop.Timeout;

public class FakedEventLoop extends StandardEventLoop {
	private final TimeFake timeFake;

	@Override
	public Timeout setTimeout_(long millis, Event c) {
		if (millis < 0)
			throw new IllegalArgumentException("Timeout cannot be negative");
		final long fakeMillis = timeFake.toReal(millis);
		return super.setTimeout_(fakeMillis, () -> c.run());
	}

	@Override
	public Instant now_() {
		return timeFake.toFake(super.now_());
	}

	public FakedEventLoop(TimeFake timeFake) {
		this.timeFake = timeFake;
	}
}
