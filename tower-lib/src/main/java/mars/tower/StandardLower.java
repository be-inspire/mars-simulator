package mars.tower;

import static eventloop.El.now;
import static eventloop.El.setTimeout;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static mars.messages.Anomaly.CYLINDER_BUSY;
import static mars.messages.PsState.ASSIGNED;
import static mars.messages.PsState.CLEARING_CYLINDER;
import static mars.messages.PsState.FAILED_PS;
import static mars.messages.PsState.IN_PROGRESS;
import static mars.messages.PsState.IN_TRANSIT_TO_ASSIGNED;
import static mars.messages.PsState.IN_TRANSIT_TO_OLD_ASSIGNED;
import static mars.messages.PsState.LANDED;
import static mars.messages.PsState.LANDING_AUTHORIZED;
import static mars.messages.PsState.LIFT_OFF;
import static mars.messages.PsState.PLATFORM_ENGAGED;
import static mars.messages.PsState.READY_TO_LIFT_OFF;
import static mars.messages.PsState.WAITING_LANDING_PERMISSION;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import eventloop.El;
import mars.agent.OnlineStatus;
import mars.heartbeat.AgentLifecycleListener;
import mars.messages.AnomalyConfirm;
import mars.messages.AnomalyIndication;
import mars.messages.DroneLandedConfirm;
import mars.messages.DroneLandedIndication;
import mars.messages.PayloadBay;
import mars.messages.PlatformAssignmentConfirm;
import mars.messages.PlatformAssignmentIndication;
import mars.messages.PlatformCylinderEnterRequest;
import mars.messages.PlatformCylinderEnterResponse;
import mars.messages.PlatformCylinderLeftConfirm;
import mars.messages.PlatformCylinderLeftIndication;
import mars.messages.PlatformQuitRequest;
import mars.messages.PlatformQuitResponse;
import mars.messages.PlatformReachabilityConfirm;
import mars.messages.PlatformReachabilityIndication;
import mars.messages.PlatformStatusConfirm;
import mars.messages.PlatformStatusIndication;
import mars.messages.PlatformStatusRequest;
import mars.messages.PlatformStatusResponse;
import mars.messages.PsAbortConfirm;
import mars.messages.PsAbortIndication;
import mars.messages.PsCompletedConfirm;
import mars.messages.PsCompletedIndication;
import mars.messages.PsDemandRequest;
import mars.messages.PsDemandResponse;
import mars.messages.PsPlatformAvailability;
import mars.messages.PsPlatformReachability;
import mars.messages.PsState;
import mars.messages.ReadyPsRequest;
import mars.messages.ReadyPsResponse;
import mars.messages.UnexpectedPsStateException;
import mars.tower.PlanUpdater.Actions;
import mars.tower.comms.McMessageReceiver;
import mars.tower.comms.PlatformMessageReceiver;
import mars.utils.ListenerNotifier;

/**
 * The standard {@link Lower} implementation.
 *
 * @author mperrando
 *
 */
public class StandardLower extends AbstractLower<PitStop> implements Lower, Actions {

	private final Consumer<Throwable> onError;
	private int nextPsId = 1;
	private final PlanUpdater planUpdater = new PlanUpdater(this);
	private final long psLandingWaitMillis;
	private final ListenerNotifier<PsListener> listenerNotifier = new ListenerNotifier<>();
	private final long halfMarginMillis;
	private final PsListener repeater = new PsListener() {
		@Override
		public void stateUpdate(PitStop ps, PsState old, PsState state) {
			listenerNotifier.notifyListeners(l -> l.stateUpdate(ps, old, state));
		}

		@Override
		public void logUpdated(PitStop ps, PsLog log) {
			listenerNotifier.notifyListeners(l -> l.logUpdated(ps, log));
		}

		@Override
		public void reachabilitiesUpdate(PitStop ps, List<PsPlatformReachability> old) {
			listenerNotifier.notifyListeners(l -> l.reachabilitiesUpdate(ps, old));
		}
	};

	public StandardLower(WorldModel worldModel,
			BiFunction<PlatformMessageReceiver, McMessageReceiver, TowerMessaging> messagingMaker,
			Consumer<AgentLifecycleListener> allConsumer, Consumer<Throwable> onError, long commsTimeoutTwr,
			long landingWaitMillis, long halfMarginMillis) {
		super(worldModel, messagingMaker, allConsumer, commsTimeoutTwr);
		this.psLandingWaitMillis = landingWaitMillis;
		this.halfMarginMillis = halfMarginMillis;
		this.onError = onError;
		this.addPsListener(new PsListenerAdapter() {
			@Override
			public void stateUpdate(PitStop ps, PsState old, PsState state) {
				info(ps, "{} --> {}", old, state);
			}
		});
		worldModel.addPlatforStatusListener(new PlatformStatusListener() {

			@Override
			public void psStatusUpdate(PitStop ps) {
			}

			@Override
			public void platformStatusUpdate(PlatformStatus old, PlatformStatus current) {
				onPlatformStatusUpdate(old, current);
			}
		});
	}

	@Override
	public void addPsListener(PsListener psListener) {
		listenerNotifier.addListener(psListener);
	}

	@Override
	public void abortPs(final PitStop ps) {
		abortPs(ps, PsState.ABORTING);
	}

	@Override
	public void assign(PitStop ps, Assignation assignation) {
		info(ps, "assign assignation: {}", assignation);
		ps.checkState(() -> {
			ps.assign(of(assignation));
			if (ps.is(PsState.READY_TO_SCHEDULE, ASSIGNED))
				ps.setState(ASSIGNED);
			else {
				debug(ps, "cancelling preparePsTimeout: {}", ps.preparePsTimeout);
				ps.preparePsTimeout.cancel();
				ps.tooLateOnArrivalTimeout.cancel();
				ps.setState(IN_TRANSIT_TO_OLD_ASSIGNED);
			}
			final PlatformAssignmentIndication i = new PlatformAssignmentIndication(ps.id, ps.pltId(),
					ps.arrivalTime());
			sendPlatformAssignmentIndication(ps.mcId, ps, i);
		}, e -> {
			error(ps, e, "cannot assign");
			if (!isEngaged(ps)) {
				info(ps, "aborting because it is not engaged");
				abortPs(ps);
			} else {
				warn(ps, "NOT abort because it is engaged");
			}
		}, PsState.READY_TO_SCHEDULE, ASSIGNED, IN_TRANSIT_TO_ASSIGNED, IN_TRANSIT_TO_OLD_ASSIGNED);
	}

	public void prepareOnPlatform(PitStop ps) {
		ps.checkState(() -> {
			sendReadyPsRequest(ps.pltId(), new ReadyPsRequest(ps.id, ps.getBayId()), ps);
			ps.engaged();
			ps.setState(PLATFORM_ENGAGED);
		}, e -> {
			error(ps, e, "cannot send ready ps request, aborting");
			abortPs(ps);
		}, IN_TRANSIT_TO_ASSIGNED);
	}

	@Override
	public void planUpdated(Plan plan) {
		planUpdater.execute(plan);
	}

	@Override
	public void addPlatforStatusListener(PlatformStatusListener l) {
		worldModel.addPlatforStatusListener(l);
	}

	@Override
	public Collection<PlatformStatus> getPlatformsStatus() {
		return worldModel.getPlatformsStatus();
	}

	@Override
	public Collection<PitStop> getPitStops() {
		return worldModel.getPitStops();
	}

	@Override
	public boolean isEngaged(PitStop ps) {
		return ps.isEngaged();
	}

	@Override
	public boolean isCylinderBusy(PitStop ps) {
		return ps.hasState(LANDED, IN_PROGRESS, READY_TO_LIFT_OFF, LIFT_OFF, FAILED_PS, CLEARING_CYLINDER);
	}

	@Override
	protected PsDemandResponse onPsDemandRequest(final String mcId, final PsDemandRequest request)
			throws NoAvailabilityException {
		LOGGER.info("<<-[{}] {}", mcId, request);
		final var ps = new PitStop(nextPsId++);
		LOGGER.debug("PS: {}", ps);
		insertNewPitstop(ps);
		ps.addPsListener(repeater);
		ps.request(mcId, now(), request);
		final String pt = request.payloadType();
		final List<PsPlatformAvailability> platforms = platformsForPayloadType(pt);
		LOGGER.debug("Plt with payload type: {}: {}", pt, platforms);
		if (platforms.isEmpty())
			throw new NoAvailabilityException("No platforms with payload type: " + pt);
		return new PsDemandResponse(request.requestId(), ps.id, platforms);
	}

	@Override
	protected PlatformReachabilityConfirm onPlatformReachabilityIndication(PlatformReachabilityIndication p) {
		LOGGER.info("<<- {} ", p);
		final PitStop ps = worldModel.getPitStop(p.psId()).get();
		LOGGER.debug("PS: {}", ps);
		if (p.psPlatformReachabilities().isEmpty()) {
			error(ps, new NoSuchElementException(), "Empty psPlatformReachabilities. Aborting.");
			abortPs(ps);
		} else {
			ps.updateReachability(p.psPlatformReachabilities());
			ps.checkState(() -> {
				if (PsState.REQUESTED.equals(ps.getState()))
					ps.setState(PsState.READY_TO_SCHEDULE);
				worldModel.notifyPsUpdated(ps);
			}, e -> {
				error(ps, e, "Invalid state");
				abortPs(ps);
			}, PsState.REQUESTED, IN_TRANSIT_TO_ASSIGNED);
		}
		return new PlatformReachabilityConfirm();
	}

	@Override
	protected void onPlatformAssignmentConfirm(PlatformAssignmentConfirm r, PitStop ps) {
		info(ps, "<<- {}", r);
		ps.checkState(() -> {
			final Instant maxArrivalTime = getMaxArrivalTime(ps);
			if (maxArrivalTime.isAfter(now())) {
				ps.tooLateOnArrivalTimeout = setTimeout(now().until(maxArrivalTime, MILLIS), () -> {
					error(ps, new TimeoutException(), "Aborting for timeout on arrival time");
					abortPs(ps);
				});
				if (schedulePreparePitStop(ps))
					ps.setState(IN_TRANSIT_TO_ASSIGNED);
				else
					abortPs(ps);
			} else {
				error(ps, new IllegalStateException(), "Arrival time: {} is in the past", maxArrivalTime);
				abortPs(ps);
			}
		}, e -> {
			error(ps, e, "Invalid state");
			abortPs(ps);
		}, ASSIGNED, IN_TRANSIT_TO_OLD_ASSIGNED);
	}

	@Override
	protected void onPlatformAssignmentIndicationError(Throwable t, PitStop ps) {
		error(ps, t, "PlatformAssignmentError");
		abortPsWithoutConfirm(ps);
	}

	@Override
	protected void onPlatformAssignmentIndicationTimeout(PitStop ps) {
		error(ps, new TimeoutException(), "PlatformAssignmentTimeout");
		abortPsWithoutConfirm(ps);
	}

	@Override
	protected void onPlatformCylinderEnterRequest(PlatformCylinderEnterRequest r,
			CompletableFuture<PlatformCylinderEnterResponse> f) {
		LOGGER.info("<<- {}", r);
		processPsOrFail(r.psId(), f, ps -> {
			LOGGER.debug("PS: {}", ps);
			ps.tooLateOnArrivalTimeout.cancel();
			ps.pendingCylinderEntereFuture(f);
			ps.checkState(() -> {
				final Instant maxArrivalTime = getMaxArrivalTime(ps);
				LOGGER.debug("maxArrivalTime: {}", maxArrivalTime);
				final Instant minArrivalTime = getMinArrivalTime(ps);
				LOGGER.debug("minArrivalTime: {}", minArrivalTime);
				if (minArrivalTime.isAfter(now()) || maxArrivalTime.isBefore(now())) {
					error(ps, new TimeoutException(),
							"Aborting for not in time on arrival, expected at: {} arrival at: {}", ps.arrivalTime(),
							now());
					ps.failCylinderEnterFutureIfPending(new RuntimeException("PS not in time on arrival"));
					abortPs(ps);
				} else {
					if (isReadyPsOnPlatform(ps)) {
						info(ps, "platform is ready");
						authorizeLanding(ps);
					} else if (isEngaged(ps)) {
						info(ps, "platform is preparing ");
						ps.waitingPsReadyTimeout = setTimeout(now().until(maxArrivalTime, MILLIS), () -> {
							error(ps, new TimeoutException(), "platform did not prepare PS in time, aborting");
							ps.failCylinderEnterFutureIfPending(new TimeoutException("Platform is not ready"));
							abortPs(ps);
						});
						ps.setState(WAITING_LANDING_PERMISSION);
					} else {
						error(ps, new IllegalStateException(), "platform is not ready, aborting");
						ps.failCylinderEnterFutureIfPending(new RuntimeException("Platform is not ready"));
						abortPs(ps);
					}
				}
			}, e -> {
				ps.failCylinderEnterFutureIfPending(e);
				abortPs(ps);
			}, PLATFORM_ENGAGED);
		});
	}

	protected void onPlatformStatusUpdate(PlatformStatus old, PlatformStatus current) {
		final String pltId = current.pltId();
		if (!isAvailable(current)) {
			abortPsAssignedToPlt(pltId);
		} else {
			checkPitstopToAbortForMissingBay(pltId);
			processReadyPsOnPlatform(current);
		}
	}

	@Override
	protected void onReadyPsResponse(String destAgentName, ReadyPsResponse r, PitStop ps) {
		info(ps, "<<- {}", r);
	}

	@Override
	protected void onReadyPsRequestError(Throwable t, PitStop ps) {
		error(ps, t, "ReadyPsRequestError");
		LOGGER.debug("", t);
		abortPs(ps);
	}

	@Override
	protected void onReadyPsRequestTimeout(PitStop ps) {
		error(ps, new TimeoutException(), "ReadyPsRequestTimeout");
		abortPs(ps);
	}

	@Override
	protected void onPsAbortToMcConfirm(String destAgentName, PsAbortConfirm r, PitStop ps) {
		info(ps, "PsAbortToMcConfirm from MC");
		ps.checkState(() -> processPsAbortConfirmFromMc(ps), e -> {
			error(ps, e, "not changing state");
		}, PsState.ABORTING, PsState.FAILED_PS);
	}

	@Override
	protected void onPsAbortToMcIndicationTimeout(PitStop ps) {
		error(ps, new TimeoutException(), "PsAbortTimeout. PS will be marked as ABORTED anyway.");
		processPsAbortConfirmFromMc(ps);
	}

	@Override
	protected void onPsAbortToMcIndicationError(Throwable t, PitStop ps) {
		error(ps, t, "PsAbortConfirmError. PS will be marked as ABORTED anyway.");
		processPsAbortConfirmFromMc(ps);
	}

	@Override
	protected void onPsAbortToPltConfirm(String destAgentName, PsAbortConfirm r, PitStop ps) {
		info(ps, "PsAbortConfirm from PLT: {} ", destAgentName);
	}

	@Override
	protected void onPsAbortToPltIndicationError(Throwable t, PitStop ps) {
		error(ps, t, "PsAbortToPltConfirmError from PLT");
	}

	@Override
	protected void onPsAbortToPltIndicationTimeout(PitStop ps) {
		error(ps, new TimeoutException(), "PsAbortToPltTimeout from PLT");
	}

	@Override
	protected void onDroneLandedIndication(DroneLandedIndication i, CompletableFuture<DroneLandedConfirm> f) {
		LOGGER.info("<<- {}", i);
		processPsOrFail(i.psId(), f, ps -> {
			LOGGER.debug("PS: {}", ps);
			ps.pendingDroneLandedFuture(f);
			ps.checkNotState(() -> {
				ps.checkState(() -> {
					ps.setState(PsState.LANDED);
					ps.landingWaitTimeout.cancel();
					final DroneLandedIndication ind = new DroneLandedIndication(ps.id, ps.pltId(), i.droneId());
					sendDroneLandedIndication(ps.pltId(), ind, ps);

				}, e -> {
					ps.failDroneLandedFutureIfPending(e);
					abortPs(ps);
				}, LANDING_AUTHORIZED);
			}, t -> {
				warn(ps, "landed indication but ps failed");
				ps.failDroneLandedFutureIfPending(t);
			}, PsState.FAILED_PS, PsState.CLEARING_CYLINDER);
		});
	}

	@Override
	protected void onDroneLandedConfirm(String destAgentName, DroneLandedConfirm p, PitStop ps) {
		info(ps, "<<- {}", p);
		ps.checkState(() -> {
			ps.setState(PsState.IN_PROGRESS);
			if (!ps.completeDroneLandedFutureIfPending(new DroneLandedConfirm())) {
				error(ps, new IllegalStateException(), "There is no pending completeDroneLandedFutureIfPending");
				abortPs(ps);
			}
		}, e -> {
			ps.failDroneLandedFutureIfPending(e);
			abortPs(ps);
		}, PsState.LANDED);
	}

	@Override
	protected void onDroneLandedIndicationError(Throwable t, PitStop ps) {
		if (ps.failDroneLandedFutureIfPending(t.getCause())) {
			error(ps, t, "DroneLandedError");
			abortPs(ps);
		} else {
			error(ps, t, "DroneLandedError but not pending PS");
		}
	}

	@Override
	protected void onDroneLandedIndicationTimeout(PitStop ps) {
		if (ps.failDroneLandedFutureIfPending(new RuntimeException("Platform timed out"))) {
			error(ps, new TimeoutException(), "DroneLandedTimeout");
			abortPs(ps);
		} else {
			error(ps, new TimeoutException(), "DroneLandedTimeout but not pending PS");
		}
	}

	@Override
	protected void onPsCompletedIndication(PsCompletedIndication p, CompletableFuture<PsCompletedConfirm> f) {
		processPsOrFail(p.psId(), f, ps -> {
			info(ps, "PsCompletedIndication");
			ps.pendingPsCompletedFuture(f);
			ps.checkState(() -> {
				final PsCompletedIndication ind = new PsCompletedIndication(ps.id, ps.pltId());
				ps.setState(PsState.READY_TO_LIFT_OFF);
				sendPsCompletedIndication(ps.mcId, ind, ps);
			}, e -> {
				ps.failPsCompletedFutureIfPending(e);
				abortPs(ps);
			}, PsState.IN_PROGRESS);
		});
	}

	@Override
	protected void onPsCompletedConfirm(String destAgentName, PsCompletedConfirm c, PitStop ps) {
		info(ps, "<<- {}", c);
		ps.checkState(() -> {
			ps.setState(PsState.LIFT_OFF);
			if (!ps.completePsCompletedFutureIfPending(new PsCompletedConfirm())) {
				error(ps, new IllegalStateException(), "There is no pending completePsCompletedFutureIfPending");
				abortPs(ps);
			}
		}, e -> {
			ps.failPsCompletedFutureIfPending(e);
			abortPs(ps);
		}, PsState.READY_TO_LIFT_OFF);

	}

	@Override
	protected void onPsCompletedIndicationError(Throwable t, PitStop ps) {
		if (ps.failPsCompletedFutureIfPending(t)) {
			error(ps, t, "PsCompletedError");
			abortPsWithoutConfirm(ps);
		} else {
			error(ps, t, "PsCompletedError on not pending PS");
		}
	}

	@Override
	protected void onPsCompletedIndicationTimeout(PitStop ps) {
		if (ps.failPsCompletedFutureIfPending(new RuntimeException("MC timed out"))) {
			error(ps, new TimeoutException(), "PsCompletedTimeout");
			abortPsWithoutConfirm(ps);
		} else {
			error(ps, new TimeoutException(), "PsCompletedTimeout on not pending PS");
		}
	}

	@Override
	protected PlatformCylinderLeftConfirm onPlatformCylinderLeftIndication(PlatformCylinderLeftIndication ind) {
		LOGGER.info("<<- {}", ind);
		final PitStop ps = worldModel.getPitStop(ind.psId()).get();
		LOGGER.debug("PS: {}", ps);
		ps.checkState(() -> {
			if (PsState.LIFT_OFF.equals(ps.getState()))
				ps.setState(PsState.ENDED);
			else
				ps.setState(PsState.ABORTED);
			scheduleBurying(ps);
		}, e -> {
			abortPs(ps);
		}, PsState.LIFT_OFF, PsState.CLEARING_CYLINDER);
		return new PlatformCylinderLeftConfirm();
	}

	@Override
	protected void onPlatformStatusResponse(String pltName, PlatformStatusResponse r) {
		LOGGER.info("<<- {}", r);
		final mars.messages.PlatformStatus status = r.status().normalize();
		final var s = new PlatformStatus(pltName, status.readyPsId(), r.geoCoord(), OnlineStatus.ONLINE,
				status.alarms(), status.serviceMillis(), status.payloadBays());
		worldModel.updatePlatform(pltName, s);
	}

	@Override
	protected void onPlatformDiscovered(String agentName) {
		sendPlatformStatusRequest(agentName, new PlatformStatusRequest());
	}

	@Override
	protected void onPlatformLost(String agentName) {
		worldModel.updatePlatform(agentName,
				new PlatformStatus(agentName, null, null, OnlineStatus.LOST, null, 0, null));
	}

	@Override
	protected void onPlatformStatusRequestError(Throwable t) {
		LOGGER.warn(
				"Error in PlatformStatusResponse. No more requests will be done. Waiting for spontaneous indication.",
				t);
	}

	@Override
	protected void onPlatformStatusRequestTimeout() {
		LOGGER.warn(
				"Timeout in PlatformStatusResponse. No more requests will be done. Waiting for spontaneous indication.");
	}

	@Override
	protected PlatformStatusConfirm onPlatformStatusIndication(final String pltId,
			final PlatformStatusIndication indication) {
		LOGGER.info("<<- {}", indication);
		final var receivedStatus = indication.status().normalize();
		LOGGER.debug("receivedStatus: {}", receivedStatus);
		if (!validate(pltId, receivedStatus)) {
			LOGGER.error("status: {} contains one or more unknown bay ids", indication);
			throw new IllegalArgumentException("Indication contains one or more unknown bay ids");
		}
		updatePlatformStatus(pltId, receivedStatus);
		return new PlatformStatusConfirm();
	}

	@Override
	protected PsAbortConfirm onPsAbortIndication(PsAbortIndication p) throws UnexpectedPsStateException {
		LOGGER.info("<<- {}", p);
		final PitStop ps = worldModel.getPitStop(p.psId()).get();
		LOGGER.debug("PS: {}", ps);
		ps.checkState(PsState.REQUESTED, PsState.READY_TO_SCHEDULE, ASSIGNED, IN_TRANSIT_TO_ASSIGNED,
				IN_TRANSIT_TO_OLD_ASSIGNED, WAITING_LANDING_PERMISSION);
		processPsAbortConfirmFromMc(ps);
		return new PsAbortConfirm();
	}

	@Override
	protected PlatformQuitResponse onPlatformQuitRequest(String pltName, PlatformQuitRequest r) {
		// TODO Auto-generated method stub
		LOGGER.info("<<- {}", r);
		return null;
	}

	@Override
	protected void onAnomalyConfirm(String destAgentName, AnomalyConfirm r, PitStop ps) {
		info(ps, "PLT has confirmed anomaly");
	}

	@Override
	protected void onAnomalyIndicationError(Throwable t, PitStop ps) {
		error(ps, t, "ignoring anomaly indication error");
	}

	@Override
	protected void onAnomalyIndicationTimeout(PitStop ps) {
		error(ps, new TimeoutException(), "ignoring anomaly indication timeout");
	}

	private void insertNewPitstop(PitStop pitStop) {
		worldModel.insertPitStop(pitStop);
	}

	private List<PsPlatformAvailability> platformsForPayloadType(String payloadType) {
		final List<PsPlatformAvailability> result = worldModel.getPlatformsStatus().stream()
				.filter(s -> hasPayloadType(s, payloadType))
				.map(s -> new PsPlatformAvailability(s.pltId(), s.geoCoord())).collect(Collectors.toList());
		return result;
	}

	private boolean hasPayloadType(PlatformStatus s, String payloadType) {
		return s.payloadBays().stream()
				.anyMatch(pb -> ofNullable(pb.payload()).map(b -> b.payloadType().equals(payloadType)).orElse(false));
	}

	private void updatePlatformStatus(final String pltName, final mars.messages.PlatformStatus receivedStatus) {
		LOGGER.info("Updating platform status");
		var s = worldModel.getPlatformStatus(pltName);
		final List<PayloadBay> bays = mergeBays(s.payloadBays(), receivedStatus.payloadBays());
		s = s.updateStatus(receivedStatus.readyPsId(), OnlineStatus.ONLINE, receivedStatus.alarms(), bays);
		worldModel.updatePlatform(pltName, s);
	}

	private List<PayloadBay> mergeBays(List<PayloadBay> actual, List<PayloadBay> incoming) {
		final var byId = incoming.stream().collect(Collectors.toMap(b -> b.bayId(), identity()));
		return actual.stream().map(b -> byId.getOrDefault(b.bayId(), b)).collect(Collectors.toList());
	}

	private void abortPsAssignedToPlt(String pltId) {
		worldModel.getPitStops().stream().filter(p -> pltId.equals(p.pltId())).filter(p -> !isEngaged(p))
				.forEach(this::abortPs);
	}

	private void abortPsWithoutConfirm(final PitStop ps) {
		info(ps, "Aborting without confirm");
		processPsAbortConfirmFromMc(ps);
		abortPltIfEngaged(ps);
	}

	private void abortPltIfEngaged(final PitStop ps) {
		debug(ps, "checking PLT engaged");
		if (isEngaged(ps)) {
			info(ps, "aborting on PLT");
			sendPsAbortToPlt(ps.pltId(), new PsAbortIndication(ps.id), ps);
		}
	}

	private Instant getMaxArrivalTime(PitStop ps) {
		return ps.arrivalTime().plus(halfMarginMillis, MILLIS);
	}

	private Instant getMinArrivalTime(PitStop ps) {
		return ps.arrivalTime().minus(halfMarginMillis, MILLIS);
	}

	private void processPsAbortConfirmFromMc(final PitStop ps) {
		if (ps.hasState(PsState.FAILED_PS)) {
			info(ps, "is FAILED_PS. Setting CLEARING_CYLINDER");
			ps.setState(PsState.CLEARING_CYLINDER);
		} else {
			ps.setState(PsState.ABORTED);
			scheduleBurying(ps);
		}
	}

	private void scheduleBurying(final PitStop ps) {
		if (isEngaged(ps)) {
			final long millis = El.now().until(ps.getAssignation().get().occupiedTo(), MILLIS);
			if (millis > 0) {
				info(ps, "scheduling burying in {} ms", millis);
				El.setTimeout(millis, () -> bury(ps));
			} else {
				bury(ps);
			}
		} else {
			bury(ps);
		}
	}

	private void bury(PitStop ps) {
		worldModel.buryPitStop(ps);
		worldModel.notifyPsUpdated(ps);
		info(ps, "has been buryied");
	}

	private void processPsOrFail(int psId, CompletableFuture<?> f, Consumer<PitStop> c) {
		worldModel.getPitStop(psId).ifPresentOrElse(c,
				() -> f.completeExceptionally(new NoSuchElementException("PS: " + psId + " not present")));
	}

	private void onLandedIndicationTimedOut(PitStop ps) {
		error(ps, new TimeoutException(), "Aborting for timeout on landing");
		ps.setDroneLandedIndicationTimedOut();
		abortPs(ps, PsState.FAILED_PS);
	}

	private static void debug(PitStop ps, String msg, Object... arguments) {
		LOGGER.debug("[PS {}] " + msg, makeArgs(ps, arguments));
		LOGGER.debug("{}", ps);
	}

	private static void info(PitStop ps, String msg, Object... arguments) {
		LOGGER.info("[PS {}] " + msg, makeArgs(ps, arguments));
		LOGGER.debug("{}", ps);
	}

	private static void warn(PitStop ps, String msg, Object... arguments) {
		LOGGER.warn("[PS {}] " + msg, makeArgs(ps, arguments));
		LOGGER.debug("{}", ps);
	}

	private void error(PitStop ps, Throwable t, String msg, Object... arguments) {
		LOGGER.error("[PS {}] " + msg, makeArgs(ps, t, arguments));
		LOGGER.debug("{}", ps);
		onError.accept(t);
	}

	private static Object[] makeArgs(PitStop ps, Object... arguments) {
		final Object[] args = new Object[arguments.length + 1];
		args[0] = ps.id;
		System.arraycopy(arguments, 0, args, 1, arguments.length);
		return args;
	}

	private static Object[] makeArgs(PitStop ps, Throwable t, Object... arguments) {
		final Object[] args = new Object[arguments.length + 2];
		args[0] = ps.id;
		System.arraycopy(arguments, 0, args, 1, arguments.length);
		args[args.length - 1] = t;
		return args;
	}

	private void abortPs(final PitStop ps, PsState nextState) {
		info(ps, "Aborting PS passing through state: {}", nextState);
		ps.checkNotState(() -> {
			ps.preparePsTimeout.cancel();
			ps.waitingPsReadyTimeout.cancel();
			ps.tooLateOnArrivalTimeout.cancel();
			abortPltIfEngaged(ps);
			markCylinderBusyIfItIs(ps);
			ps.setState(nextState);
			sendPsAbortToMc(ps.mcId, new PsAbortIndication(ps.id), ps);
		}, t -> {
			error(ps, t, "Cannot abort with state: {}", ps.getState());
		}, PsState.ABORTING, PsState.ABORTED, PsState.ENDED);
	}

	private void markCylinderBusyIfItIs(PitStop ps) {
		if (isCylinderBusy(ps)) {
			error(ps, new RuntimeException(), "TODO");
			sendAnomalyIndication(ps.pltId(), new AnomalyIndication(EnumSet.of(CYLINDER_BUSY)), ps);
		}
	}

	private void processReadyPsOnPlatform(PlatformStatus current) {
		if (current.readyPsId() != null) {
			worldModel.getPitStop(current.readyPsId()).ifPresent(p -> {
				debug(p, "completeCylinderEnterFutureIfPending");
				p.checkState(() -> authorizeLanding(p), null, PsState.WAITING_LANDING_PERMISSION);
				p.completeCylinderEnterFutureIfPending(new PlatformCylinderEnterResponse());
				debug(p, "cancelling waitingPsReadyTimeout: {}", p.waitingPsReadyTimeout);
				p.waitingPsReadyTimeout.cancel();
			});
		}
	}

	private boolean isAvailable(PlatformStatus current) {
		return current.status() == OnlineStatus.ONLINE && !current.alarms().isUnavailable();
	}

	private void authorizeLanding(PitStop ps) {
		info(ps, "authorized landing");
		ps.completeCylinderEnterFutureIfPending(new PlatformCylinderEnterResponse());
		ps.setState(LANDING_AUTHORIZED);
		ps.landingWaitTimeout = setTimeout(psLandingWaitMillis, () -> onLandedIndicationTimedOut(ps));
	}

	private boolean isReadyPsOnPlatform(PitStop ps) {
		final Integer currentPsId = worldModel.getPlatformStatus(ps.pltId()).readyPsId();
		return currentPsId != null && currentPsId == ps.id;
	}

	private boolean validate(String pltId, mars.messages.PlatformStatus receivedStatus) {
		final Set<Integer> incoming = receivedStatus.payloadBays().stream().map(b -> b.bayId())
				.collect(Collectors.toSet());
		final Collection<?> actual = worldModel.getPlatformStatus(pltId).payloadBays().stream().map(b -> b.bayId())
				.collect(Collectors.toSet());
		incoming.removeAll(actual);
		return incoming.isEmpty();
	}

	private void checkPitstopToAbortForMissingBay(String pltId) {
		final var s = worldModel.getPitStops().stream().filter(p -> p.hasState(ASSIGNED, IN_TRANSIT_TO_ASSIGNED))
				.filter(p -> p.pltId().equals(pltId)).collect(toMap(p -> p.getAssignation().get().bay(), identity()));
		for (final var b : worldModel.getPlatformStatus(pltId).payloadBays()) {
			if (b.payload() == null) {
				final var ba = new BayAssignation(pltId, b.bayId());
				final PitStop ps = s.get(ba);
				if (ps != null)
					abortPs(ps);
			}
		}
	}

	private boolean schedulePreparePitStop(PitStop ps) {
		final PayloadBay bay = bayForPs(ps);
		final Instant sendInstant = ps.arrivalTime().minus(halfMarginMillis, MILLIS)
				.minus(Duration.of(bay.prepareMillis(), MILLIS));
		info(ps, "should schedule ready request at:{} ", sendInstant);
		final long millis = now().until(sendInstant, MILLIS);
		final boolean canSchedule = millis >= 0;
		if (canSchedule) {
			info(ps, "scheduling ready request in {} ms", millis);
			ps.preparePsTimeout = setTimeout(millis, () -> {
				prepareOnPlatform(ps);
			});
		} else {
			warn(ps, "scheduling of ready request is in the past ({} ms)", millis);
		}
		return canSchedule;
	}

	private PayloadBay bayForPs(PitStop ps) {
		final PayloadBay bay = worldModel.getPlatformStatus(ps.pltId()).payloadBays().stream()
				.filter(b -> b.bayId() == ps.getBayId()).findFirst().get();
		return bay;
	}
}
