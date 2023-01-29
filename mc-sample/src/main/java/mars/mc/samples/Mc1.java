package mars.mc.samples;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eventloop.El;
import mars.mc.McComms;
import mars.mc.McCommsListener;
import mars.mc.McLogics;
import mars.mc.McSideComms;
import mars.mc.McUtils;
import mars.messages.DroneLandedConfirm;
import mars.messages.DroneLandedIndication;
import mars.messages.PlatformAssignmentConfirm;
import mars.messages.PlatformAssignmentIndication;
import mars.messages.PlatformAvailabilityConfirm;
import mars.messages.PlatformAvailabilityIndication;
import mars.messages.PlatformCylinderEnterRequest;
import mars.messages.PlatformCylinderEnterResponse;
import mars.messages.PlatformCylinderLeftConfirm;
import mars.messages.PlatformCylinderLeftIndication;
import mars.messages.PlatformReachabilityIndication;
import mars.messages.PsAbortConfirm;
import mars.messages.PsAbortIndication;
import mars.messages.PsCompletedIndication;
import mars.messages.PsDemandRequest;
import mars.messages.PsDemandResponse;
import mars.messages.PsPlatformAvailability;
import mars.messages.PsPlatformReachability;
import mars.side.signals.DroneLandedEvent;
import mars.side.signals.DroneTookOffEvent;
import mars.side.signals.Events;
import mars.side.signals.PayloadOnBoard;
import mars.signals.Signals;
import mars.utils.FileSeededRanodmSupplier;
import mars.utils.Repo;

/**
 * A simple {@link McLogics} implementation that simulates a {@link PitStop}
 * request in a random time when the previous one fails or is completed.
 * 
 * @author mperrando
 *
 */
public class Mc1 implements McLogics<PitStop>, McCommsListener<PitStop> {

	final static Logger LOGGER = LoggerFactory.getLogger(Mc1.class);

	public static void main(String[] args) throws Exception {
		McUtils.main(args, new Mc1(new FileSeededRanodmSupplier(new File("mc1-rand.json"))));
	}

	private final Repo<PitStop> repo = new Repo<>();
	private final Repo.Index<PitStop, Integer> psById = repo.addIndex("by id", ps -> ps.pitStopId);

	private int nextRequestId;
	private final int timeToCylinderSurface = 10000;

	private McComms<PitStop> comms;
	private McSideComms sideComms;
	private final Random random;
	private final ExponentialDistribution exponentialDistribution;

	public Mc1(Supplier<Random> randomSupplier) {
		LOGGER.info("Using random: " + randomSupplier);
		this.random = randomSupplier.get();
		final JDKRandomGenerator rng = new JDKRandomGenerator();
		rng.setSeed(random.nextLong());
		exponentialDistribution = new ExponentialDistribution(rng, 20_000);
	}

	@Override
	public void start(McComms<PitStop> comms, McSideComms sideComms) {
		this.comms = comms;
		comms.setListener(this);
		this.sideComms = sideComms;
		if (comms.isTowerActive()) {
			LOGGER.info("Tower is active, scheduling next PS");
			simulateNewRequestInRandomTime();
		} else {
			LOGGER.info("Waiting for tower is active for scheduling next PS");
		}
	}

	@Override
	public PlatformAssignmentConfirm onPlatformAssignmentIndication(PlatformAssignmentIndication p) {
		LOGGER.info("{}", p);
		final PitStop ps = searchPs(p.psId());
		ps.platformId = p.platformId();
		ps.platformAvailableAt = El.now();

		final var timeOfArrival = p.at();
		final var theoricTravelTimeInMillis = El.now().until(timeOfArrival, ChronoUnit.MILLIS);
		final var delay = -Math.min(10_000, theoricTravelTimeInMillis) + (long) exponentialDistribution.sample();
		final var travelTimeInMillis = theoricTravelTimeInMillis + delay;
		LOGGER.info("Sending the drone to platform: {} for PS: {} in: {} ms (instead of {})", ps.platformId,
				ps.pitStopId, travelTimeInMillis, theoricTravelTimeInMillis);
		LOGGER.debug("{}", ps);
		ps.travelTimeout.ifPresent(t -> t.cancel());

		ps.travelTimeout = Optional.of(El.setTimeout(travelTimeInMillis, () -> droneArrivedAtPlatform(ps)));
		return new PlatformAssignmentConfirm();
	}

	@Override
	public PlatformAvailabilityConfirm onPlatformAvailabilityIndication(PlatformAvailabilityIndication p) {
		LOGGER.info("{}", p);
		final PitStop ps = searchPs(p.psId());

		final List<PsPlatformAvailability> availabilities = p.psPlatformAvailabilities();
		final List<PsPlatformReachability> reachabilities = availabilities.stream().map(this::calculateReachability)
				.collect(Collectors.toList());

		return new PlatformAvailabilityConfirm(ps.pitStopId, reachabilities);
	}

	@Override
	public void onCylinderLeftConfirmed(PlatformCylinderLeftConfirm c, PitStop ps) {
		LOGGER.info("{}", c);
		LOGGER.debug("{}", ps);
		simulateNewRequestInRandomTime();
	}

	@Override
	public void onCylinderLeftError(Throwable t, PitStop ps) {
		LOGGER.error("Cylinder Left Indication got an error for PS: {}", ps.pitStopId, t);
		LOGGER.debug("{}", ps);
	}

	@Override
	public void onCylinderLeftTimeout(PitStop ps) {
		LOGGER.error("Cylinder Left Indication timed out for PS, request id: {}", ps.requestedAt);
		LOGGER.debug("{}", ps);
	}

	@Override
	public void onPsDemandTimeout(PitStop ps) {
		LOGGER.error("PS Demand Request timed out for PS {}!", ps.pitStopId);
		LOGGER.debug("{}", ps);
	}

	@Override
	public void onPsDemandError(Throwable t, PitStop ps) {
		LOGGER.error("PS Demand Request got an error for PS: " + ps.pitStopId, t);
		LOGGER.debug("{}", ps);
		simulateNewRequestInRandomTime();
	}

	@Override
	public void onPsDemandResponse(PsDemandResponse r, PitStop ps) {
		LOGGER.info("<<- {}", r);
		LOGGER.debug("{}", ps);
		ps.pitStopId = r.psId();
		repo.add(ps);

		final List<PsPlatformAvailability> availabilities = r.psPlatformAvailabilities();
		final List<PsPlatformReachability> reachabilities = availabilities.stream().map(this::calculateReachability)
				.collect(Collectors.toList());

		final PlatformReachabilityIndication platformReachabilityIndication = new PlatformReachabilityIndication(
				ps.pitStopId, reachabilities);

		comms.sendPlatformReachabilityIndication(ps, platformReachabilityIndication);
	}

	@Override
	public void onPlatformReachabilityConfirm(PitStop ps) {
		LOGGER.info("Platform Reachability Confirm for PS: {}", ps.pitStopId);
		LOGGER.debug("{}", ps);
	}

	@Override
	public void onCylinderEnterResponse(PlatformCylinderEnterResponse r, PitStop ps) {
		LOGGER.info("{} for PS: {}", r, ps.pitStopId);
		LOGGER.debug("{}", ps);
		ps.authorizedEnterCylinderAt = El.now();
		landDrone(ps);
	}

	@Override
	public void onDroneLandedConfirmed(DroneLandedConfirm r, PitStop ps) {
		LOGGER.info("{} for PS: {}", r, ps.pitStopId);
		LOGGER.debug("{}", ps);
		ps.landedAt = El.now();
	}

	@Override
	public void onDroneLandedTimeout(PitStop ps) {
		LOGGER.error("Drone landed indication timed out for PS: {}", ps.pitStopId);
		LOGGER.debug("{}", ps);
	}

	@Override
	public void onDroneLandedError(Throwable t, PitStop ps) {
		LOGGER.error("Drone landed indication got an error for PS: {}", ps.pitStopId, t);
		LOGGER.debug("{}", ps);
	}

	@Override
	public void onCylinderEnterTimeout(PitStop ps) {
		LOGGER.error("Cylinder enter Request timed out for PS: {}", ps.pitStopId);
		LOGGER.debug("{}", ps);
	}

	@Override
	public void onCylinderEnterError(Throwable t, PitStop ps) {
		LOGGER.error("Drone cannot enter platform: {} cylynder for PS: {}. Taking drone to a safe position",
				ps.platformId, ps.pitStopId, t);
		LOGGER.debug("{}", ps);
	}

	@Override
	public PsCompletedIndication onPsCompletedIndication(PsCompletedIndication p) {
		LOGGER.info("{}", p);
		final PitStop ps = searchPs(p.psId());

		if (!ps.platformId.equals(p.platformId()))
			throw new RuntimeException("Bad platform for PS: " + ps.pitStopId + " expected: " + ps.platformId
					+ " but got: " + p.platformId());

		LOGGER.info("Completed PS: {}", ps.pitStopId);
		LOGGER.debug("{}", ps);
		takeOff(p.platformId(), ps);

		return new PsCompletedIndication(ps.pitStopId, ps.platformId);
	}

	@Override
	public PsAbortConfirm onPsAbortIndication(PsAbortIndication p) {
		LOGGER.info("{}", p);
		final var ps = psById.get(p.psId());
		LOGGER.debug("{}", ps);
		ps.travelTimeout.ifPresent(t -> {
			t.cancel();
			LOGGER.info("Aborted travel towards platform for PS {}. Taking drone to a safe position", p.psId());
		});
		simulateNewRequestInRandomTime();
		return new PsAbortConfirm();
	}

	@Override
	public void onPlatformReachabilityTimeout(PitStop ps) {
		LOGGER.error("Platforms reachability timed out for PS: {}", ps.pitStopId);
		LOGGER.debug("{}", ps);
	}

	@Override
	public void onPlatformReachabilityError(Throwable t, PitStop ps) {
		LOGGER.error("Platforms reachability got an error PS: {}", ps.pitStopId, t);
		LOGGER.debug("{}", ps);
	}

	private PitStop searchPs(int psId) {
		final var ps = psById.get(psId);
		if (ps == null)
			throw new IllegalArgumentException("Unexpected PS id " + psId);
		return ps;
	}

	private void takeOff(String platformId, final PitStop ps) {
		LOGGER.info("Drone takes off and will leave platform {} cylinder in {}ms", ps.platformId,
				timeToCylinderSurface);

		sideComms.emitSideSignal(Signals.EVENT.name(), Events.MARS_DRONE_TOOK_OFF,
				new DroneTookOffEvent(ps.drone.id(), ps.platformId));
		El.setTimeout(timeToCylinderSurface, () -> {
			ps.leftCylinderAt = El.now();
			comms.sendPlatformCylinderLeftIndication(new PlatformCylinderLeftIndication(ps.pitStopId, platformId), ps);
		});
	}

	private void simulateNewRequestInRandomTime() {
		final int seconds = random.nextInt(5) + 3;
		LOGGER.info("Simulating next PS request in {} seconds", seconds);
		El.setTimeout(seconds * 1000, this::sendPsRequest);
	}

	private void sendPsRequest() {
		final var ps = new PitStop(nextRequestId++,
				new Drone("DRONE-XY", new PayloadOnBoard(UUID.randomUUID().toString(), "PT1", .234)), El.now());
		comms.sendPsDemandRequest(new PsDemandRequest(ps.requestId, "PT1", El.now().plus(20, ChronoUnit.MINUTES)), ps);
	}

	private void landDrone(PitStop ps) {
		LOGGER.info("Drone will land on platform {} in {} ms", ps.platformId, timeToCylinderSurface);

		El.setTimeout(timeToCylinderSurface, () -> {
			sideComms.emitSideSignal(Signals.EVENT.name(), Events.MARS_DRONE_LANDED,
					new DroneLandedEvent(ps.drone.id(), ps.platformId, ps.drone.payloadOnBoard()));
			comms.sendDroneLandedIndication(new DroneLandedIndication(ps.pitStopId, ps.platformId, ps.drone.id()), ps);
		});
	}

	private void droneArrivedAtPlatform(PitStop ps) {
		LOGGER.info("Drone arrived at platform: {} for PS: {}", ps.platformId, ps.pitStopId);
		LOGGER.debug("{}", ps);
		ps.arrivedAtPlatformCylinder = El.now();
		final PlatformCylinderEnterRequest req = new PlatformCylinderEnterRequest(ps.pitStopId, ps.platformId);
		comms.sendPlatformCylinderEnterRequest(req, ps);
	}

	private PsPlatformReachability calculateReachability(PsPlatformAvailability a) {
		final Instant etaMin = El.now().plus(Duration.ofMinutes(1));
		final Instant etaMax = El.now().plus(Duration.ofMinutes(2));
		return new PsPlatformReachability(a.platformId(), etaMin, etaMax);
	}

	@Override
	public void onTowerDiscovered(String agentName) {
		LOGGER.info("Tower has become active, scheduling next PS");
		simulateNewRequestInRandomTime();
	}

	@Override
	public void onTowerLost(String agentName) {
	}

	@Override
	public void onTowerReturned(String agentName) {
	}
}
