package mars.tower.planner.dummy;

import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.joda.time.Interval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mars.messages.PayloadBay;
import mars.messages.PsPlatformReachability;
import mars.tower.Assignation;
import mars.tower.BayAssignation;
import mars.tower.PayloadAvailability;
import mars.tower.PitStop;
import mars.tower.Plan;
import mars.tower.Planner;
import mars.tower.PlatformStatus;

/**
 * A simple Planner that allocates PitStops as early as possible, givinig
 * priority to the ones where the drones dies first.
 * 
 * @author mperrando
 *
 */
public class DummyPlanner implements Planner {

	private final static Logger LOGGER = LoggerFactory.getLogger(DummyPlanner.class);

	private final IntervalCalculator intervalCalculator = new IntervalCalculator();
	private final long halfMarginMillis;

	public DummyPlanner(long halfMarginMillis) {
		this.halfMarginMillis = halfMarginMillis;
	}

	@Override
	public Plan plan(Instant t0, Collection<PitStop> plannables, Collection<PitStop> planned,
			Collection<PlatformStatus> platformStates) {
		LOGGER.info("Planning");
		LOGGER.debug("Plannebles: {}", plannables);
		LOGGER.debug("Planed: {}", planned);
		LOGGER.debug("With plts: {}", platformStates);
		final var assignations = planned.stream().map(p -> p.getAssignation()).map(Optional::get).collect(toSet());
		final var availables = platformStates.stream()
				.flatMap(s -> s.payloadBays().stream().filter(b -> b.payload() != null).map(b -> make(s, b)))
				.filter(a -> !used(a, assignations)).collect(groupingBy(PayloadAvailability::pltId));
		final var pltById = platformStates.stream().collect(toMap(PlatformStatus::pltId, identity()));
		final var plannedByPltName = planned.stream().collect(groupingBy(p -> p.pltId()));
		final var occupationByPltName = plannedByPltName.entrySet().stream()
				.collect(toMap(e -> e.getKey(), e -> intervalOf(e.getValue())));

		final Plan plan = new Plan(plannables);
		plannables.stream().sorted(this::earlyEndOfLife)
				.forEach(p -> plan(t0, plan, p, availables, occupationByPltName, pltById));
		LOGGER.info("Planned");
		LOGGER.debug("Plan: {}", plan);
//		try {
//			Thread.sleep(3000);
//		} catch (final InterruptedException e1) {
//			e1.printStackTrace();
//		}
		return plan;
	}

	private int earlyEndOfLife(PitStop p1, PitStop p2) {
		return p1.request.endOfDroneLife().compareTo(p2.request.endOfDroneLife());
	}

	private List<Interval> intervalOf(List<PitStop> val) {
		return val.stream().map(PitStop::getAssignation).map(Optional::get).map(this::intervalOfAssignation)
				.collect(toList());
	}

	private Interval intervalOfAssignation(Assignation a) {
		final var intervalStart = toJoda(a.occupiedFrom());
		final var intervalEnd = toJoda(a.occupiedTo());
		return new Interval(intervalStart, intervalEnd);
	}

	private void plan(Instant t0, Plan plan, PitStop p, Map<String, List<PayloadAvailability>> availables,
			Map<String, List<Interval>> occupationByPltName, Map<String, PlatformStatus> pltById) {
		LOGGER.debug("Planning PS: {}\nwith availabiliteis: {}\noccupations: {}", p, availables, occupationByPltName);
		final List<PsPlatformReachability> reachabilities = p.getReachabilities();
		final var solutions = reachabilities
				.stream().flatMap(r -> plan(t0, p, r, availables.get(r.platformId()),
						occupationByPltName.get(r.platformId()), pltById.get(r.platformId())))
				.sorted((s1, s2) -> s1.at().compareTo(s2.at())).collect(toList());

		LOGGER.debug("Valid solutions: {}", solutions);

		final Optional<Assignation> maybeSolution = solutions.isEmpty() ? Optional.empty()
				: Optional.of(solutions.get(0));

		LOGGER.debug("Choosen solution: {}", maybeSolution);

		if (maybeSolution.isPresent()) {
			final var solution = maybeSolution.get();
			plan.add(p, solution);
			final String assignedPltId = solution.bay().pltId();
			final int assignedBayId = solution.bay().bayId();

			availables.get(assignedPltId).removeIf(a -> a.bay().bayId() == assignedBayId);

			if (!occupationByPltName.containsKey(assignedPltId))
				occupationByPltName.put(assignedPltId, new LinkedList<>());
			occupationByPltName.get(assignedPltId).add(intervalOfAssignation(solution));
		}
	}

	private Stream<Assignation> plan(Instant t0, PitStop ps, PsPlatformReachability rchblt,
			Collection<PayloadAvailability> availabilities, List<Interval> occupations, PlatformStatus pltStatus) {
		final List<Interval> occupations2 = occupations == null ? Collections.emptyList() : occupations;
		final Collection<PayloadAvailability> availabilities2 = availabilities == null ? Collections.emptyList()
				: availabilities;

		LOGGER.debug("Planning reachability: {} with availabiliteis: {} occupations: {}", rchblt, availabilities,
				occupations);

		return availabilities2.stream().flatMap(a -> solutionsFor(t0, ps, rchblt, a, occupations2, pltStatus));
	}

	private Stream<Assignation> solutionsFor(Instant t0, PitStop ps, PsPlatformReachability rchblt,
			PayloadAvailability av, List<Interval> occupations, PlatformStatus pltStatus) {
		final long serviceMillis = pltStatus.serviceMillis();
		final Interval interval = new Interval(
				toJoda(rchblt.etaMin().minus(halfMarginMillis, MILLIS).minus(av.bay().prepareMillis(), MILLIS)),
				toJoda(rchblt.etaMax().plus(serviceMillis, MILLIS).plus(halfMarginMillis, MILLIS)
						.plus(av.bay().prepareMillis(), MILLIS)));

		LOGGER.debug("Rchblt with eta: [{}/{}] has interval: {}", rchblt.etaMin(), rchblt.etaMax(), interval);
		final Collection<Interval> pltSlots = intervalCalculator.subtract(Collections.singleton(interval), occupations);
		LOGGER.debug("PLT slots: {}", pltSlots);

		final Collection<Interval> pltSlotsInFuture = pltSlots.stream()
				.map(i -> new Interval(Math.max(i.getStartMillis(), t0.toEpochMilli()), i.getEndMillis()))
				.collect(toList());
		LOGGER.debug("PLT slots in future: {}", pltSlotsInFuture);

		return pltSlotsInFuture.stream()//
				.filter(slot -> hasCorrectPayloadTypeForPs(av, ps))//
				.filter(slot -> enoughTimeToServePs(slot, av, serviceMillis))//
				.filter(slot -> av.at().isBefore(latestStartInInterval(slot, av, serviceMillis)))//
				.map(slot -> makeAssignation(rchblt, slot, av, serviceMillis));
	}

	private Assignation makeAssignation(PsPlatformReachability r, Interval s, PayloadAvailability a,
			long serviceMillis) {
		final long prepareMillis = a.bay().prepareMillis();
		final Instant at = toJava(max(s.getStartMillis() + halfMarginMillis + prepareMillis,
				a.at().toEpochMilli() + prepareMillis, r.etaMin().toEpochMilli()));
		return new Assignation(new BayAssignation(a.pltId(), a.bay().bayId()), at,
				at.minus(halfMarginMillis + prepareMillis, MILLIS),
				at.plus(serviceMillis + halfMarginMillis + prepareMillis, MILLIS));
	}

	private Instant latestStartInInterval(Interval s, PayloadAvailability av, long serviceMillis) {
		return toJava(s.getEndMillis()).minus(serviceMillis, MILLIS).minus(2 * halfMarginMillis, MILLIS)
				.minus(2 * av.bay().prepareMillis(), MILLIS);
	}

	private boolean hasCorrectPayloadTypeForPs(PayloadAvailability a, PitStop p) {
		return a.bay().payload().payloadType().equals(p.getPayloadType());
	}

	private boolean enoughTimeToServePs(Interval s, PayloadAvailability av, long serviceMillis) {
		return s.toDurationMillis() >= serviceMillis + 2 * halfMarginMillis + 2 * av.bay().prepareMillis();
	}

	private long max(long... epochs) {
		return Arrays.stream(epochs).max().getAsLong();
	}

	private Instant toJava(long millis) {
		return Instant.ofEpochMilli(millis);
	}

	private org.joda.time.Instant toJoda(Instant i) {
		return new org.joda.time.Instant(i.toEpochMilli());
	}

	private boolean used(PayloadAvailability a, Set<Assignation> assignations) {
		return assignations.stream().anyMatch(it -> it.bay().equals(new BayAssignation(a.pltId(), a.bay().bayId())));
	}

	private PayloadAvailability make(PlatformStatus s, PayloadBay b) {
		return new PayloadAvailability(s.pltId(), b, availableAt(b));
	}

	private Instant availableAt(PayloadBay b) {
		final Instant i = b.payload().willRestoreAt();
		return i == null ? Instant.ofEpochMilli(0) : i;
	}
}
