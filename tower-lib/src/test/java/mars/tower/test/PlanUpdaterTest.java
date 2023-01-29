package mars.tower.test;

import static java.time.Duration.of;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import mars.messages.PsState;
import mars.tower.PitStop;
import mars.tower.Plan;
import mars.tower.PlanUpdater;
import mars.tower.PlanUpdater.Actions;
import mars.utils.test.TestUtils;

@ExtendWith(MockitoExtension.class)
class PlanUpdaterTest {

	@Mock
	private Actions actions;

	private PlanUpdater out;

	private final UpdaterTestEnv te = new UpdaterTestEnv();

	private final Collection<PitStop> plannables = new ArrayList<>();

	private final Plan plan = new Plan(plannables);

	static {
		TestUtils.loadLog4jStandard();
	}

	@BeforeEach
	void setup() {
		out = new PlanUpdater(actions);
	}

	@Test
	void testSimpleAssignment() {
		// given
		final PitStop p1 = te.ps(23, "MC1", of(1, MINUTES), te.request(12, "PT1", of(20, MINUTES)), b -> {
			b.state(PsState.READY_TO_SCHEDULE);
		});
		plan.add(p1, te.assigned("PLT1", 3, te.after(3, MINUTES)));

		// when
		out.execute(plan);

		// then
		verify(actions).assign(p1, te.assigned("PLT1", 3, te.after(3, MINUTES)));
	}

	@Test
	void testSameAssignment() {
		// given
		final PitStop p1 = te.ps(23, "MC1", of(1, MINUTES), te.request(12, "PT1", of(20, MINUTES)), b -> {
			b.state(PsState.IN_TRANSIT_TO_ASSIGNED).assignation("PLT1", 3, te.after(3, MINUTES));
		});
		plan.add(p1, te.assigned("PLT1", 3, te.after(3, MINUTES)));

		// when
		out.execute(plan);

		// then
		verifyNoInteractions(actions);
	}

	@Test
	void testAnotherTime() {
		// given
		final PitStop p1 = te.ps(23, "MC1", of(1, MINUTES), te.request(12, "PT1", of(20, MINUTES)), b -> {
			b.state(PsState.IN_TRANSIT_TO_ASSIGNED).assignation("PLT1", 3, te.after(3, MINUTES));
		});
		plan.add(p1, te.assigned("PLT1", 3, te.after(5, MINUTES)));

		// when
		out.execute(plan);

		// then
		verify(actions).assign(p1, te.assigned("PLT1", 3, te.after(5, MINUTES)));
	}

	@Test
	void testAnotherBay() {
		// given
		final PitStop p1 = te.ps(23, "MC1", of(1, MINUTES), te.request(12, "PT1", of(20, MINUTES)), b -> {
			b.state(PsState.IN_TRANSIT_TO_ASSIGNED).assignation("PLT1", 3, te.after(3, MINUTES));
		});
		plan.add(p1, te.assigned("PLT4", 1, te.after(7, MINUTES)));

		// when
		out.execute(plan);

		// then
		verify(actions).assign(p1, te.assigned("PLT4", 1, te.after(7, MINUTES)));
	}

	@Test
	void testNotPresentInPlan() {
		// given
		final PitStop p1 = te.ps(23, "MC1", of(1, MINUTES), te.request(12, "PT1", of(20, MINUTES)), b -> {
			b.state(PsState.IN_TRANSIT_TO_ASSIGNED).assignation("PLT1", 3, te.after(3, MINUTES));
		});
		final PitStop p2 = te.ps(24, "MC2", of(1, MINUTES), te.request(12, "PT1", of(20, MINUTES)), b -> {
			b.state(PsState.IN_TRANSIT_TO_ASSIGNED).assignation("PLT4", 3, te.after(3, MINUTES));
		});
		te.worldModel.setPitStops(p1);
		plannables.add(p1);
		plan.add(p2, te.assigned("PLT4", 1, te.after(7, MINUTES)));

		// when
		out.execute(plan);

		// then
		verify(actions).abortPs(p1);
		verify(actions).assign(p2, te.assigned("PLT4", 1, te.after(7, MINUTES)));
	}

	@Test
	void testNotPresentInPlanAdeitherInPlannable() {
		// given
		final PitStop p1 = te.ps(23, "MC1", of(1, MINUTES), te.request(12, "PT1", of(20, MINUTES)), b -> {
			b.state(PsState.IN_TRANSIT_TO_ASSIGNED).assignation("PLT1", 3, te.after(3, MINUTES));
		});
		final PitStop p2 = te.ps(24, "MC2", of(1, MINUTES), te.request(12, "PT1", of(20, MINUTES)), b -> {
			b.state(PsState.IN_TRANSIT_TO_ASSIGNED).assignation("PLT4", 3, te.after(3, MINUTES));
		});
		te.worldModel.setPitStops(p1);
		plan.add(p2, te.assigned("PLT4", 1, te.after(7, MINUTES)));

		// when
		out.execute(plan);

		// then
		verify(actions).assign(p2, te.assigned("PLT4", 1, te.after(7, MINUTES)));
		Mockito.verifyNoMoreInteractions(actions);
	}
}
