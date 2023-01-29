package mars.time;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

public class TimeFake {
	private final long speedFactor;
	private final Instant t0;

	public TimeFake(long speedFactor) {
		this.speedFactor = speedFactor;
		final ZonedDateTime startOfDay = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS);
		t0 = startOfDay.toInstant();
	}

	public Duration toFake(Duration d) {
		return d.multipliedBy(speedFactor);
	}

	public Duration toReal(Duration d) {
		return d.dividedBy(speedFactor);
	}

	public Instant toFake(Instant i) {
		final var realDuration = Duration.between(t0, i);
		final var fakeDuration = toFake(realDuration);
		return t0.plus(fakeDuration);
	}

	public Instant toReal(Instant i) {
		final var fakeDuration = Duration.between(t0, i);
		final var realDuration = toReal(fakeDuration);
		return t0.plus(realDuration);
	}

	public long toReal(long millis) {
		return millis / speedFactor;
	}
}
