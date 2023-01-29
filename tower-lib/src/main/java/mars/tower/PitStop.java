package mars.tower;

import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.Optional.of;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eventloop.El;
import eventloop.Timeout;
import mars.messages.DroneLandedConfirm;
import mars.messages.PlatformCylinderEnterResponse;
import mars.messages.PsCompletedConfirm;
import mars.messages.PsDemandRequest;
import mars.messages.PsPlatformReachability;
import mars.messages.PsState;
import mars.messages.UnexpectedPsStateException;
import mars.utils.ListenerNotifier;

public class PitStop {

	public static class Builder {
		private final PitStop it;

		public Builder(int id, String mcId, Instant requestedAt, PsDemandRequest request) {
			it = new PitStop(id);
			it.request(mcId, requestedAt, request);
		}

		public Builder state(PsState state) {
			it.state = state;
			return this;
		}

		public Builder reachabilities(List<PsPlatformReachability> reachabilities, Instant t) {
			it.reachabilities = reachabilities;
			it.reachabilitiesUpdatedAt = t;
			return this;
		}

		public Builder assignation(String platformId, int bayId, Instant at) {
			return assignation(platformId, bayId, at, at.minus(1, MINUTES), at.plus(5, MINUTES));
		}

		public Builder assignation(String platformId, int bayId, Instant at, Instant occupiedFrom, Instant occipiedTo) {
			it.assignation = of(new Assignation(new BayAssignation(platformId, bayId), at, occupiedFrom, occipiedTo));
			return this;
		}

		public PitStop build() {
			return it;
		}

		public Builder engaged() {
			it.engaged();
			return this;
		}
	}

	protected static final Logger LOGGER = LoggerFactory.getLogger(PitStop.class);

	private List<PsPlatformReachability> reachabilities = new ArrayList<>();

	public final int id;
	// public long serviceMillis;
	private PsState state = PsState.INIT;
	public String mcId;
	public PsDemandRequest request;
	private Optional<Assignation> assignation = Optional.empty();
	public Instant requestedAt;
	public Instant reachabilitiesUpdatedAt;

	private CompletableFuture<PlatformCylinderEnterResponse> cylinderEntereFuture;
	public CompletableFuture<PsCompletedConfirm> psCompletedFuture;

	private CompletableFuture<DroneLandedConfirm> droneLandedFuture;
	private final ListenerNotifier<PsListener> listenerNotifier = new ListenerNotifier<>();
	private final LinkedList<PsLog> psLogs = new LinkedList<>();

	private boolean droneLandedIndicationTimedOut;

	public Timeout tooLateOnArrivalTimeout = Timeout.NULL;

	public Timeout waitingPsReadyTimeout = Timeout.NULL;

	public Timeout landingWaitTimeout = Timeout.NULL;

	public Timeout preparePsTimeout = Timeout.NULL;

	private boolean engaged;

	public boolean isDroneLandedIndicationTimedOut() {
		return droneLandedIndicationTimedOut;
	}

	public void setDroneLandedIndicationTimedOut() {
		this.droneLandedIndicationTimedOut = true;
		log(El.now(), new PsLog.DroneLandedIndicationTimedOut());
	}

	public PitStop(int id) {
		this.id = id;
	}

	public void addPsListener(PsListener l) {
		listenerNotifier.addListener(l);
	}

	public void addAllPsListener(List<PsListener> psListeners) {
		listenerNotifier.addAllListener(psListeners);
	}

	public void request(String mcId, Instant requestedAt, PsDemandRequest request) {
		this.mcId = mcId;
		this.request = request;
		this.requestedAt = requestedAt;
		this.setState(PsState.REQUESTED, requestedAt);
	}

	public void updateReachability(List<PsPlatformReachability> psPlatformReachabilities) {
		LOGGER.debug("[PS: {}] Reachability update {} ", id, psPlatformReachabilities);
		this.reachabilities = psPlatformReachabilities;
		reachabilitiesUpdatedAt = El.now();
		log(El.now(), new PsLog.ReachabilitiesUpdate(psPlatformReachabilities));
	}

	public List<PsPlatformReachability> getReachabilities() {
		return reachabilities;
	}

	public String getPayloadType() {
		return request.payloadType();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + id;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final PitStop other = (PitStop) obj;
		if (id != other.id)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "PitStop [id=" + id + ", state=" + getState() + ", mcId=" + mcId + ", request=" + request
				+ ", assignation=" + assignation + ", requestedAt=" + requestedAt + ", reachabilities=" + reachabilities
				+ ", reachabilitiesUpdatedAt=" + reachabilitiesUpdatedAt + "]";
	}

	public void pendingPsCompletedFuture(CompletableFuture<PsCompletedConfirm> f) {
		if (psCompletedFuture != null)
			throw new RuntimeException("There is another pending psCompletedFuture: " + psCompletedFuture);
		psCompletedFuture = f;
	}

	public boolean completePsCompletedFutureIfPending(PsCompletedConfirm r) {
		try {
			final boolean result = psCompletedFuture != null;
			if (result)
				psCompletedFuture.complete(r);
			return result;
		} finally {
			psCompletedFuture = null;
		}
	}

	public boolean failPsCompletedFutureIfPending(Throwable t) {
		try {
			return failIfPending(t, psCompletedFuture);
		} finally {
			psCompletedFuture = null;
		}
	}

	public void pendingDroneLandedFuture(CompletableFuture<DroneLandedConfirm> f) {
		if (droneLandedFuture != null)
			throw new RuntimeException("There is another pending droneLandedFuture: " + droneLandedFuture);
		droneLandedFuture = f;
	}

	public boolean completeDroneLandedFutureIfPending(DroneLandedConfirm r) {
		try {
			final boolean result = droneLandedFuture != null;
			if (result)
				droneLandedFuture.complete(r);
			return result;
		} finally {
			droneLandedFuture = null;
		}
	}

	public boolean failDroneLandedFutureIfPending(Throwable t) {
		try {
			return failIfPending(t, droneLandedFuture);
		} finally {
			droneLandedFuture = null;
		}
	}

	public void pendingCylinderEntereFuture(CompletableFuture<PlatformCylinderEnterResponse> f) {
		if (cylinderEntereFuture != null)
			throw new RuntimeException("There is another pending cylinderEntereFuture: " + cylinderEntereFuture);
		cylinderEntereFuture = f;

	}

	public boolean completeCylinderEnterFutureIfPending(PlatformCylinderEnterResponse response) {
		try {
			final boolean result = cylinderEntereFuture != null;
			if (result)
				cylinderEntereFuture.complete(response);
			return result;
		} finally {
			cylinderEntereFuture = null;
		}
	}

	public boolean failCylinderEnterFutureIfPending(Throwable t) {
		try {
			return failIfPending(t, cylinderEntereFuture);
		} finally {
			cylinderEntereFuture = null;
		}
	}

	public void setState(final PsState state) {
		setState(state, El.now());
	}

	private void setState(final PsState state, Instant time) {
		final PsState old = this.getState();
		this.state = state;
		log(time, new PsLog.StateUpdate(old, state));
		listenerNotifier.notifyListeners(l -> l.stateUpdate(this, old, state));
	}

	public void checkState(Runnable ifMathces, Consumer<UnexpectedPsStateException> ifNotMatches,
			PsState... expectedStates) {
		try {
			checkState(ifNotMatches != null, expectedStates);
			ifMathces.run();
		} catch (final UnexpectedPsStateException e) {
			if (ifNotMatches != null) {
				ifNotMatches.accept(e);
			}
		}
	}

	public void checkState(PsState... expectedStates) throws UnexpectedPsStateException {
		checkState(true, expectedStates);
	}

	private void checkState(boolean isError, PsState... expectedStates) throws UnexpectedPsStateException {
		LOGGER.debug("Checking states: {} on: {}", Arrays.toString(expectedStates), this);
		if (!hasState(expectedStates)) {
			if (isError)
				LOGGER.error("PS: {} has different state: {}. Expected are: {}", id, state,
						Arrays.toString(expectedStates));
			else
				LOGGER.debug("PS: {} has different state: {}. Expected are: {}", id, state,
						Arrays.toString(expectedStates));
			throw new UnexpectedPsStateException(
					"Expecting one of these states: " + Arrays.toString(expectedStates) + " but was: " + state);
		}
	}

	public void checkNotState(Runnable ifMathces, Consumer<UnexpectedPsStateException> ifNotMatches,
			PsState... expectedStates) {
		try {
			checkNotState(expectedStates);
			ifMathces.run();
		} catch (final UnexpectedPsStateException e) {
			ifNotMatches.accept(e);
		}
	}

	public void checkNotState(PsState... unexpectedStates) throws UnexpectedPsStateException {
		LOGGER.debug("Checking not states: {} on: {}", Arrays.toString(unexpectedStates), this);
		if (hasState(unexpectedStates))
			throw new UnexpectedPsStateException(
					"Expecting none of these states: " + Arrays.toString(unexpectedStates) + " but was: " + state);
	}

	public boolean hasState(PsState... expectedStates) {
		return Arrays.asList(expectedStates).contains(state);
	}

	public PsState getState() {
		return state;
	}

	public boolean is(PsState... states) {
		return hasState(states);
	}

	public String pltId() {
		return assignation.map(a -> a.bay()).map(b -> b.pltId()).orElse(null);
	}

	public Instant arrivalTime() {
		return assignation.map(a -> a.at()).orElse(null);
	}

	public Optional<Integer> assignedBay() {
		return assignation.map(a -> a.bay()).map(b -> b.bayId());
	}

	public void assign(Optional<Assignation> assignation) {
		this.assignation = assignation;
		log(El.now(), new PsLog.Assigned(assignation));
	}

	public Optional<Assignation> getAssignation() {
		return assignation;
	}

	public int getBayId() {
		return assignation.map(a -> a.bay()).map(b -> b.bayId()).orElse(null);
	}

	private boolean failIfPending(Throwable t, CompletableFuture<?> f) {
		final boolean result = f != null;
		if (result)
			f.completeExceptionally(t);
		return result;
	}

	private void log(Instant instant, Object message) {
		final PsLog log = new PsLog(instant, message);
		psLogs.add(log);
		listenerNotifier.notifyListeners(l -> l.logUpdated(this, log));
	}

	public Collection<PsLog> logs() {
		return psLogs;
	}

	public void dumpLogs(PrintWriter w) {
		psLogs.forEach(l -> w.printf("%s - %s", l.instant(), l.message()));
	}

	public void buried() {
		log(El.now(), new PsLog.Buried());
	}

	public void engaged() {
		engaged = true;
	}

	public boolean isEngaged() {
		return engaged;
	}
}
