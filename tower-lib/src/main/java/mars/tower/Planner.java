package mars.tower;

import java.time.Instant;
import java.util.Collection;

/**
 * This class plans the pit stops.
 * 
 * @author mperrando
 *
 */
public interface Planner {

	Planner NULL = new Planner() {
		@Override
		public Plan plan(Instant t0, Collection<PitStop> plannables, Collection<PitStop> planned,
				Collection<PlatformStatus> platformStates) {
			return new Plan(plannables);
		}
	};

	/**
	 * Calculates the {@link Plan}
	 * 
	 * @param t0             the t0 instant beofre which nothing can be planned.
	 * @param plannables     the {@link PitStop}s to plan
	 * @param planned        the {@link PitStop}s already planned
	 * @param platformStates the states of the platforms
	 * @return the calculated {@link Plan}
	 */
	Plan plan(Instant t0, Collection<PitStop> plannables, Collection<PitStop> planned,
			Collection<PlatformStatus> platformStates);
}
