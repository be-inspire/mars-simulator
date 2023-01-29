package mars.tower.test;

import static java.time.temporal.ChronoUnit.MINUTES;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import mars.agent.OnlineStatus;
import mars.messages.GeoCoord;
import mars.messages.PayloadBay;
import mars.messages.PlatformAlarms;
import mars.messages.PsDemandRequest;
import mars.messages.PsPlatformReachability;
import mars.messages.RestoringPayload;
import mars.tower.Assignation;
import mars.tower.BayAssignation;
import mars.tower.PitStop;
import mars.tower.PitStop.Builder;
import mars.tower.Plan;
import mars.tower.PlatformStatus;

public class BaseTestEnv {

	public final Instant t0 = Instant.parse("2020-01-01T12:00:00Z");
	public final GeoCoord plt1Geo = new GeoCoord(8.5, 44.6);
	public final GeoCoord plt2Geo = new GeoCoord(8.54, 44.3);
	public final GeoCoord plt3Geo = new GeoCoord(8.51, 44.8);

	public Instant after(long n, TemporalUnit unit) {
		return t0.plus(n, unit).truncatedTo(ChronoUnit.MILLIS);
	}

	public PitStop ps(int id, String mcId, TemporalAmount requestedAfter, PsDemandRequest request,
			Consumer<Builder> c) {
		final Builder b = new Builder(id, mcId, t0.plus(requestedAfter), request);
		c.accept(b);
		return b.build();
	}

	public PsDemandRequest request(int requestId, String payloadType, TemporalAmount endOfDroneLifeAfter) {
		return new PsDemandRequest(requestId, payloadType, t0.plus(endOfDroneLifeAfter));
	}

	public List<PsPlatformReachability> withReachabilities(PsPlatformReachability... reachabilities) {
		return Arrays.asList(reachabilities);
	}

	public PsPlatformReachability reachability(String platformId, Instant t1) {
		return reachability(platformId, t1, t1.plus(1, MINUTES));
	}

	public PsPlatformReachability reachability(String platformId, Instant etaMin, Instant etaMax) {
		return new PsPlatformReachability(platformId, etaMin, etaMax);
	}

	public PlatformStatus platform1With(PayloadBay... payLoadBays) {
		return platform1With(OnlineStatus.ONLINE, payLoadBays);
	}

	public PlatformStatus platform1With(OnlineStatus s, PayloadBay... payLoadBays) {
		return new PlatformStatus("PLT1", null, s == OnlineStatus.ONLINE ? plt1Geo : null, s,
				s == OnlineStatus.ONLINE ? noAlarms() : null, s == OnlineStatus.ONLINE ? 60_000 : 0,
				s == OnlineStatus.ONLINE ? bays(payLoadBays) : null);
	}

	public PlatformStatus platform2With(PayloadBay... payLoadBays) {
		return new PlatformStatus("PLT2", null, plt2Geo, OnlineStatus.ONLINE, noAlarms(), 70_000, bays(payLoadBays));
	}

	public PlatformStatus platform3With(PayloadBay... payLoadBays) {
		return new PlatformStatus("PLT3", null, plt3Geo, OnlineStatus.ONLINE, noAlarms(), 80_000, bays(payLoadBays));
	}

	public List<PayloadBay> bays(PayloadBay... payLoadBays) {
		return Arrays.asList(payLoadBays);
	}

	public Assignation assigned(String pltId, int bayId, Instant at) {
		return assigned(pltId, bayId, at, at.minus(1, MINUTES), at.plus(5, MINUTES));
	}

	public Assignation assigned(String pltId, int bayId, Instant at, Instant occpiedFrom, Instant occpiedTo) {
		return new Assignation(new BayAssignation(pltId, bayId), at, occpiedFrom, occpiedTo);
	}

	public Assignation assigned(String pltId, int bayId, long n, TemporalUnit unit) {
		return assigned(pltId, bayId, after(n, unit));
	}

	public Plan planWith(Collection<PitStop> plannables, Consumer<Plan> c) {
		final Plan plan = new Plan(plannables);
		c.accept(plan);
		return plan;
	}

	public PayloadBay emptyBay(int bayId) {
		return new PayloadBay(bayId, null, 45_000);
	}

	public PayloadBay bay(int bayId, String type) {
		return bay(bayId, type, 1, null);
	}

	public PayloadBay bay(int bayId, String type, long prepareMillis) {
		return bay(bayId, type, 1, null, prepareMillis);
	}

	public PayloadBay bay(int bayId, String type, double d, Instant after) {
		return bay(bayId, type, d, after, 45_000);
	}

	public PayloadBay bay(int bayId, String type, double d, Instant after, long prepareMillis) {
		return new PayloadBay(bayId, new RestoringPayload("Pl100", type, d, after), prepareMillis);
	}

	protected PlatformAlarms noAlarms() {
		return new PlatformAlarms(false, false);
	}

}
