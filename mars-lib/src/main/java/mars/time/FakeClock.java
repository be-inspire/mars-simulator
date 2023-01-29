package mars.time;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public class FakeClock extends Clock {

	private final TimeFake timeFake;

	public FakeClock(TimeFake timeFake) {
		this.timeFake = timeFake;
	}

	@Override
	public ZoneId getZone() {
		return Clock.systemUTC().getZone();
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this;
	}

	@Override
	public Instant instant() {
		return timeFake.toFake(Instant.now());
	}
}