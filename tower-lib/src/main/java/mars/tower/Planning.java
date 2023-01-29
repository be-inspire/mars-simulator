package mars.tower;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eventloop.El;

public class Planning {
	private final static Logger LOGGER = LoggerFactory.getLogger(Planning.class);
	public static final Planning NULL = new Planning(Planner.NULL, Collections.emptyList(), Collections.emptyList(),
			Collections.emptyList(), p -> {
			});

	private boolean cancelled;
	private final Thread thread;

	public Planning(Planner planner, List<PitStop> plannables, List<PitStop> planned,
			Collection<PlatformStatus> platformStates, Consumer<Plan> planConsumer) {
		final long deployMillis = 10000;
		final Instant t0 = El.now().plus(deployMillis, ChronoUnit.MILLIS);
		final var executor = El.executor();
		thread = new Thread(() -> {
			LOGGER.info("Calling planner");
			final Plan plan = planner.plan(t0, plannables, planned, platformStates);

			executor.accept(() -> {
				if (cancelled)
					LOGGER.info("Planning has been cancelled");
				else
					planConsumer.accept(plan);
			});
		});
		LOGGER.info("Starting planner thread");
		thread.start();
	}

	public void cancel() {
		thread.interrupt();
		cancelled = true;
	}
}
