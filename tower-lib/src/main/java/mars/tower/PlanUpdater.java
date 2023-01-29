package mars.tower;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Updates the {@link PitStop}s with the new {@link Plan}.
 * 
 * @author mperrando
 *
 */
public class PlanUpdater {
	private final static Logger LOGGER = LoggerFactory.getLogger(PlanUpdater.class);

	/**
	 * The actions that a {@link PlanUpdater} can perform.
	 * 
	 * @author mperrando
	 *
	 */
	public interface Actions {
		/**
		 * Aborts the given {@link PitStop}.
		 * 
		 * @param ps the PS to abort
		 */
		void abortPs(PitStop ps);

		/**
		 * Assign the given {@link PitStop} with the given {@link Assignation}.
		 * 
		 * @param ps          the PS to assign
		 * @param assignation the assignation
		 */
		void assign(PitStop ps, Assignation assignation);
	}

	private final Actions actions;

	public PlanUpdater(Actions actions) {
		this.actions = actions;
	}

	/**
	 * Given the {@link Plan}, updates the {@link PitStop} through the
	 * {@link Actions}.
	 * 
	 * @param plan the plan to put online
	 */
	public void execute(Plan plan) {
		LOGGER.debug("Start sending plan");
		final Map<PitStop, Assignation> assignations = plan.assignations();
		assignations.forEach((ps, a) -> {
			if (ps.getAssignation().map(as -> as.equals(a)).orElse(false)) {
				LOGGER.debug("No change for PS: {} with assignation: {}", ps, a);
				return;
			} else {
				LOGGER.info("Assigning PS: {} -> {}", ps.id, a);
				actions.assign(ps, a);
			}
		});

		final Set<PitStop> plannedPitStops = assignations.keySet();
		plan.getPlannables().stream().filter(ps -> !plannedPitStops.contains(ps)).forEach(ps -> {
			LOGGER.info("PS: {} not present in plan. Aborting", ps.id);
			LOGGER.debug("Aborting PS: {}", ps);
			actions.abortPs(ps);
		});
		LOGGER.debug("Plan sent");
	}

}
