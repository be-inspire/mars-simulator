package mars.tower.planner.dummy;

import static java.time.Duration.ZERO;
import static java.time.Duration.of;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import mars.tower.PitStop;
import mars.tower.Plan;
import mars.tower.PlatformStatus;
import mars.tower.test.BaseTestEnv;
import mars.utils.test.TestUtils;

class DummyPlannerTest {

	private static final int SVC_MILLIS = 60;
	private final DummyPlanner out = new DummyPlanner(5_000);
	private final BaseTestEnv te = new PlannerTestEnv();

	static {
		TestUtils.loadLog4jStandard();
	}

	@Test
	void testOneNoAvailabilities() {
		final List<PitStop> plannables = pitStops(te.ps(100, "MC1", ZERO, te.request(22200, "PT1", of(1, HOURS)), p -> {
		}));
		final List<PitStop> planned = pitStops();
		final Collection<PlatformStatus> pltStates = pltSts(te.platform1With(te.bay(1, "PT2")));
		final Plan actual = out.plan(te.t0, plannables, planned, pltStates);
		isEmpty(actual);
	}

	@Test
	void testOneNoAlreadyTaken() {
		final List<PitStop> plannables = pitStops(te.ps(100, "MC1", ZERO, te.request(22200, "PT1", of(1, HOURS)), p -> {
			p.reachabilities(
					te.withReachabilities(te.reachability("PLT1", te.after(2, MINUTES), te.after(10, MINUTES))), te.t0);
		}));
		final List<PitStop> planned = pitStops(te.ps(1, "MC1", ZERO, te.request(222, "PT1", of(1, HOURS)), p -> {
			p.assignation("PLT1", 1, te.after(5, MINUTES));
		}));
		final Collection<PlatformStatus> pltStates = pltSts(te.platform1With(te.bay(1, "PT1")));

		// then
		isEmpty(out.plan(te.t0, plannables, planned, pltStates));
	}

	@Test
	void testOneNoOthers() {
		final PitStop psToPlan = te.ps(100, "MC1", ZERO, te.request(22200, "PT1", of(1, HOURS)), p -> {
			p.reachabilities(
					te.withReachabilities(te.reachability("PLT1", te.after(2, MINUTES), te.after(10, MINUTES))), te.t0);
		});
		final List<PitStop> plannables = pitStops(psToPlan);
		final List<PitStop> planned = pitStops();
		final Collection<PlatformStatus> pltStates = pltSts(te.platform1With(te.bay(1, "PT1", 50_000)));
		final Plan actual = out.plan(te.t0, plannables, planned, pltStates);
		assertEquals(te.planWith(plannables, p -> {
			p.add(psToPlan, te.assigned("PLT1", 1, te.after(120, SECONDS), te.after(120 - 5 - 50, SECONDS),
					te.after(120 + SVC_MILLIS + 50 + 5, SECONDS)));
		}), actual);
	}

	@Test
	void testDifferentPayloadTypeAvailable() {
		final PitStop psToPlan = te.ps(100, "MC1", ZERO, te.request(22200, "PT1", of(1, HOURS)), p -> {
			p.reachabilities(
					te.withReachabilities(te.reachability("PLT1", te.after(2, MINUTES), te.after(10, MINUTES))), te.t0);
		});
		final List<PitStop> plannables = pitStops(psToPlan);
		final List<PitStop> planned = pitStops();
		final Collection<PlatformStatus> pltStates = pltSts(te.platform1With(te.bay(1, "PT2")));
		final Plan actual = out.plan(te.t0, plannables, planned, pltStates);
		isEmpty(actual);
	}

	@Test
	void testAvailableAfterEtaMax() {
		final PitStop psToPlan = te.ps(100, "MC1", ZERO, te.request(22200, "PT1", of(1, HOURS)), p -> {
			p.reachabilities(
					te.withReachabilities(te.reachability("PLT1", te.after(2, MINUTES), te.after(10, MINUTES))), te.t0);
		});
		final List<PitStop> plannables = pitStops(psToPlan);
		final List<PitStop> planned = pitStops();
		final Collection<PlatformStatus> pltStates = pltSts(
				te.platform1With(te.bay(1, "PT1", .66, te.after(11, MINUTES))));
		final Plan actual = out.plan(te.t0, plannables, planned, pltStates);
		isEmpty(actual);
	}

	@Test
	void testAvailableAfterEtaMin() {
		final PitStop psToPlan = te.ps(100, "MC1", ZERO, te.request(22200, "PT1", of(1, HOURS)), p -> {
			p.reachabilities(
					te.withReachabilities(te.reachability("PLT1", te.after(2, MINUTES), te.after(10, MINUTES))), te.t0);
		});
		final List<PitStop> plannables = pitStops(psToPlan);
		final List<PitStop> planned = pitStops();
		final Collection<PlatformStatus> pltStates = pltSts(
				te.platform1With(te.bay(1, "PT1", .66, te.after(3, MINUTES))));
		final Plan actual = out.plan(te.t0, plannables, planned, pltStates);
		assertEquals(te.planWith(plannables, p -> {
			p.add(psToPlan, te.assigned("PLT1", 1, te.after(180 + 45, SECONDS), te.after(180 - 5, SECONDS),
					te.after(180 + 45 + SVC_MILLIS + 45 + 5, SECONDS)));
		}), actual);
	}

	@Test
	void testNotInThePast() {
		final PitStop psToPlan = te.ps(100, "MC1", ZERO, te.request(22200, "PT1", of(1, HOURS)), p -> {
			p.reachabilities(
					te.withReachabilities(te.reachability("PLT1", te.after(1, MINUTES), te.after(10, MINUTES))), te.t0);
		});
		final List<PitStop> plannables = pitStops(psToPlan);
		final List<PitStop> planned = pitStops();
		final Collection<PlatformStatus> pltStates = pltSts(te.platform1With(te.bay(1, "PT1", 59_000)));
		final Plan actual = out.plan(te.after(0, SECONDS), plannables, planned, pltStates);
		assertEquals(te.planWith(plannables, p -> {
			p.add(psToPlan, te.assigned("PLT1", 1, te.after(59 + 5, SECONDS), te.after(59 + 5 - 5 - 59, SECONDS),
					te.after(59 + 5 + SVC_MILLIS + 59 + 5, SECONDS)));
		}), actual);
	}

	@Test
	void testAvailableAfterEtaMinWithAnotherEndingAtAvailability() {
		final PitStop psToPlan = te.ps(100, "MC1", ZERO, te.request(22200, "PT1", of(1, HOURS)), p -> {
			p.reachabilities(
					te.withReachabilities(te.reachability("PLT1", te.after(2, MINUTES), te.after(10, MINUTES))), te.t0);
		});
		final List<PitStop> plannables = pitStops(psToPlan);
		final List<PitStop> planned = pitStops(te.ps(1, "MC1", ZERO, te.request(222, "PT1", of(1, HOURS)), p -> {
			p.assignation("PLT1", 1, te.after(2, MINUTES), te.after(120 - 5 - 45, SECONDS),
					te.after(120 + SVC_MILLIS + 45 + 5, SECONDS));
		}));
		final Collection<PlatformStatus> pltStates = pltSts(
				te.platform1With(te.bay(2, "PT1", .66, te.after(3, MINUTES))));
		final Plan actual = out.plan(te.t0, plannables, planned, pltStates);
		assertEquals(te.planWith(plannables, p -> {
			p.add(psToPlan,
					te.assigned("PLT1", 2, te.after(120 + SVC_MILLIS + 45 + 5 + 45 + 5, SECONDS),
							te.after(120 + SVC_MILLIS + 45 + 5, SECONDS),
							te.after(120 + SVC_MILLIS + 45 + 5 + 45 + 5 + SVC_MILLIS + 45 + 5, SECONDS)));
		}), actual);
	}

	@Test
	void testDontTakeTheSamePayload() {
		final PitStop psToPlan = te.ps(100, "MC1", ZERO, te.request(22200, "PT1", of(1, HOURS)), p -> {
			p.reachabilities(
					te.withReachabilities(te.reachability("PLT1", te.after(2, MINUTES), te.after(10, MINUTES))), te.t0);
		});

		final PitStop another = te.ps(101, "MC1", ZERO, te.request(22201, "PT1", of(1, HOURS)), p -> {
			p.reachabilities(
					te.withReachabilities(te.reachability("PLT1", te.after(3, MINUTES), te.after(10, MINUTES))), te.t0);
		});

		final List<PitStop> plannables = pitStops(psToPlan, another);
		final List<PitStop> planned = pitStops();

		final Collection<PlatformStatus> pltStates = pltSts(te.platform1With(te.bay(1, "PT1")));
		final Plan actual = out.plan(te.t0, plannables, planned, pltStates);

		assertEquals(
				te.planWith(plannables,
						p -> p.add(psToPlan, te.assigned("PLT1", 1, te.after(120, SECONDS),
								te.after(120 - 5 - 45, SECONDS), te.after(120 + SVC_MILLIS + 45 + 5, SECONDS)))),
				actual);
	}

	@Test
	void testDontUseOccupiedPlatform() {
		final PitStop psToPlan = te.ps(100, "MC1", ZERO, te.request(22200, "PT1", of(1, HOURS)), p -> {
			p.reachabilities(te.withReachabilities(te.reachability("PLT1", te.after(2, MINUTES), te.after(3, MINUTES))),
					te.t0);
		});

		final PitStop another = te.ps(101, "MC1", ZERO, te.request(22201, "PT2", of(1, HOURS)), p -> {
			p.reachabilities(te.withReachabilities(te.reachability("PLT1", te.after(3, MINUTES), te.after(4, MINUTES))),
					te.t0);
		});

		final List<PitStop> plannables = pitStops(psToPlan, another);
		final List<PitStop> planned = pitStops();

		final Collection<PlatformStatus> pltStates = pltSts(
				te.platform1With(te.bay(1, "PT1", 59_000), te.bay(2, "PT2", 59_000)));
		final Plan actual = out.plan(te.t0, plannables, planned, pltStates);

		assertEquals(
				te.planWith(plannables,
						p -> p.add(psToPlan, te.assigned("PLT1", 1, te.after(120, SECONDS),
								te.after(120 - 5 - 59, SECONDS), te.after(120 + SVC_MILLIS + 59 + 5, SECONDS)))),
				actual);
	}

	@Test
	void testOneAfterTheOther() {
		final PitStop ps0 = te.ps(100, "MC1", ZERO, te.request(22200, "PT1", of(1, HOURS)), p -> {
			p.reachabilities(te.withReachabilities(te.reachability("PLT1", te.after(3, MINUTES), te.after(4, MINUTES))),
					te.t0);
		});

		final PitStop ps1 = te.ps(101, "MC1", ZERO, te.request(22201, "PT2", of(1, HOURS)), p -> {
			p.reachabilities(
					te.withReachabilities(te.reachability("PLT1", te.after(2, MINUTES), te.after(10, MINUTES))), te.t0);
		});

		final PitStop ps2 = te.ps(102, "MC1", ZERO, te.request(22202, "PT3", of(1, HOURS)), p -> {
			p.reachabilities(
					te.withReachabilities(te.reachability("PLT1", te.after(2, MINUTES), te.after(10, MINUTES))), te.t0);
		});

		final List<PitStop> plannables = pitStops(ps0, ps1, ps2);
		final List<PitStop> planned = pitStops();

		final Collection<PlatformStatus> pltStates = pltSts(
				te.platform1With(te.bay(1, "PT1", 12_000), te.bay(2, "PT2", 16_000), te.bay(3, "PT3", 9_000)));
		final Plan actual = out.plan(te.t0, plannables, planned, pltStates);

		assertEquals(te.planWith(plannables, p -> {
			p.add(ps0, te.assigned("PLT1", 1, te.after(180, SECONDS), te.after(180 - 5 - 12, SECONDS),
					te.after(180 + SVC_MILLIS + 12 + 5, SECONDS)));
			// no @120 because the time window is too small
			p.add(ps1,
					te.assigned("PLT1", 2, te.after(180 + SVC_MILLIS + 12 + 5 + 5 + 16, SECONDS),
							te.after(180 + SVC_MILLIS + 12 + 5, SECONDS),
							te.after(180 + SVC_MILLIS + 12 + 5 + 5 + 16 + SVC_MILLIS + 16 + 5, SECONDS)));
			p.add(ps2, te.assigned("PLT1", 3,
					te.after(180 + SVC_MILLIS + 12 + 5 + 5 + 16 + SVC_MILLIS + 16 + 5 + 5 + 9, SECONDS),
					te.after(180 + SVC_MILLIS + 12 + 5 + 5 + 16 + SVC_MILLIS + 16 + 5, SECONDS),
					te.after(180 + SVC_MILLIS + 12 + 5 + 5 + 16 + SVC_MILLIS + 16 + 5 + 5 + 9 + SVC_MILLIS + 9 + 5,
							SECONDS)));
		}), actual);
	}

	@Test
	void testOneSecondPassesFirstIfLifeIsShorter() {
		final PitStop ps0 = te.ps(100, "MC1", ZERO, te.request(22200, "PT1", of(1, HOURS)), p -> {
			p.reachabilities(
					te.withReachabilities(te.reachability("PLT1", te.after(3, MINUTES), te.after(10, MINUTES))), te.t0);
		});

		final PitStop ps1 = te.ps(101, "MC1", ZERO, te.request(22201, "PT1", of(30, MINUTES)), p -> {
			p.reachabilities(
					te.withReachabilities(te.reachability("PLT1", te.after(3, MINUTES), te.after(10, MINUTES))), te.t0);
		});

		final List<PitStop> plannables = pitStops(ps0, ps1);
		final List<PitStop> planned = pitStops();

		final Collection<PlatformStatus> pltStates = pltSts(te.platform1With(te.bay(1, "PT1"), te.bay(2, "PT1")));
		final Plan actual = out.plan(te.t0, plannables, planned, pltStates);

		assertEquals(te.planWith(plannables, p -> {
			p.add(ps1, te.assigned("PLT1", 1, te.after(180, SECONDS), te.after(180 - 5 - 45, SECONDS),
					te.after(180 + SVC_MILLIS + 45 + 5, SECONDS)));
			// no @120 because the time window is too small
			p.add(ps0,
					te.assigned("PLT1", 2, te.after(180 + SVC_MILLIS + 5 + 45 + 5 + 45, SECONDS),
							te.after(180 + SVC_MILLIS + 45 + 5, SECONDS),
							te.after(180 + SVC_MILLIS + 5 + 45 + 5 + 45 + SVC_MILLIS + 45 + 5, SECONDS)));
		}), actual);
	}

	private void isEmpty(final Plan actual) {
		assertIterableEquals(Collections.emptyList(), actual.assignations().values());
	}

	private Collection<PlatformStatus> pltSts(PlatformStatus... s) {
		return Arrays.asList(s);
	}

	private List<PitStop> pitStops(PitStop... s) {
		return Arrays.asList(s);
	}
}
