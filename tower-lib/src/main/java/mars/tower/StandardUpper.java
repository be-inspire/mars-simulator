package mars.tower;

import static mars.messages.PsState.ASSIGNED;
import static mars.messages.PsState.INIT;
import static mars.messages.PsState.IN_TRANSIT_TO_ASSIGNED;
import static mars.messages.PsState.IN_TRANSIT_TO_OLD_ASSIGNED;
import static mars.messages.PsState.READY_TO_SCHEDULE;
import static mars.messages.PsState.REQUESTED;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mars.messages.PsPlatformReachability;
import mars.messages.PsState;

public class StandardUpper implements PlatformStatusListener {
	private final static Logger LOGGER = LoggerFactory.getLogger(StandardUpper.class);
	private final Lower lower;
	private Planning currentPlanningOperation = Planning.NULL;
	private final Planner planner;

	public StandardUpper(Lower lower, Planner planner) {
		this.planner = planner;
		this.lower = lower;
		lower.addPlatforStatusListener(this);
		lower.addPsListener(new PsListenerAdapter() {
			@Override
			public void stateUpdate(PitStop ps, PsState old, PsState state) {
				stateUpdated(ps, old, state);
			}

			@Override
			public void reachabilitiesUpdate(PitStop ps, List<PsPlatformReachability> old) {
				onReachabilitiesUpdate(ps, old);
			}
		});
	}

	protected void onReachabilitiesUpdate(PitStop ps, List<PsPlatformReachability> old) {
		if (ps.hasState(IN_TRANSIT_TO_ASSIGNED)) {
			LOGGER.info("[PS{}] planning because reachabilities update", ps.id);
			LOGGER.debug("{}", ps);
			plan();
		}
	}

	@Override
	public void platformStatusUpdate(PlatformStatus old, PlatformStatus current) {
		LOGGER.info("[{}] planning because has updated its status", current.pltId());
		LOGGER.debug("{}", current);
		plan();
	}

	@Override
	public void psStatusUpdate(PitStop pitStop) {
	}

	protected void stateUpdated(PitStop ps, PsState old, PsState state) {
		if (state == PsState.READY_TO_SCHEDULE) {
			LOGGER.info("[PS{}] planning because PS has changed its state to: {}", ps.id, state);
			LOGGER.debug("{}", ps);
			plan();
		}
	}

	private void plan() {
		LOGGER.info("Cancelling current planning operation");
		currentPlanningOperation.cancel();
		final Collection<PitStop> pitStops = lower.getPitStops();
		final List<PitStop> plannables = pitStops.stream().filter(this::isPlannable)
				.collect(Collectors.toUnmodifiableList());
		final List<PitStop> planned = pitStops.stream().filter(this::isPlanned)
				.collect(Collectors.toUnmodifiableList());
		currentPlanningOperation = new Planning(planner, plannables, planned, lower.getPlatformsStatus(), this::send);
	}

	private boolean isPlanned(PitStop p) {
		return !p.is(INIT, REQUESTED, READY_TO_SCHEDULE, ASSIGNED, IN_TRANSIT_TO_ASSIGNED, IN_TRANSIT_TO_OLD_ASSIGNED);
	}

	private boolean isPlannable(PitStop p) {
		return p.is(READY_TO_SCHEDULE, ASSIGNED, IN_TRANSIT_TO_ASSIGNED, IN_TRANSIT_TO_OLD_ASSIGNED);
	}

	private void send(Plan plan) {
		LOGGER.info("Sending plan to lower");
		lower.planUpdated(plan);
	}

}
