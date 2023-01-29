package mars.platform.logics;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eventloop.El;
import eventloop.ElUtils;
import mars.heartbeat.AgentLifecycleListener;
import mars.messages.Anomaly;
import mars.messages.AnomalyConfirm;
import mars.messages.AnomalyIndication;
import mars.messages.CannotReadyPayload;
import mars.messages.CannotStartPsException;
import mars.messages.DroneLandedConfirm;
import mars.messages.DroneLandedIndication;
import mars.messages.PayloadBay;
import mars.messages.PlatformAlarms;
import mars.messages.PlatformQuitRequest;
import mars.messages.PlatformQuitResponse;
import mars.messages.PlatformStatus;
import mars.messages.PlatformStatusConfirm;
import mars.messages.PlatformStatusIndication;
import mars.messages.PlatformStatusRequest;
import mars.messages.PlatformStatusResponse;
import mars.messages.PsAbortConfirm;
import mars.messages.PsAbortIndication;
import mars.messages.PsCompletedConfirm;
import mars.messages.PsCompletedIndication;
import mars.messages.ReadyPsRequest;
import mars.messages.ReadyPsResponse;
import mars.messages.RestoringPayload;
import mars.messages.UnexpectedDroneExcpetion;
import mars.platform.comms.StandardPlatformMessaging;
import mars.platform.comms.TowerMessageReceiver;

/**
 * Platform Logics.
 * 
 * All the platforms logics that mediates between the communications and the
 * {@link PlatformInternals} are contained here.
 * 
 * @author mperrando
 *
 */
public class PlatformLogics<B extends Bay<B>> extends AbstractPlatformLogics implements Platform {

	final static Logger LOGGER = LoggerFactory.getLogger(PlatformLogics.class);

	private PlatformInternals<B> internals = PlatformInternals.NULL();

	private final String myName;

	private final BayListener<B> rechargeListener = new BayListener<B>() {
		@Override
		public void rechargeComplete(B bay) {
			onRechargeComplete(bay);
		}

		@Override
		public void bayContentUpdate(B bay) {
			onBayContentUpdate(bay);
		}
	};

	private CompletableFuture<Void> quitFuture;

	private boolean flangeUnvavilable;

	private final PlatformEventEmitter eventEmitter;

	private Integer currentReadyPs;

	private boolean cylinderBusy;

	private final long serviceTimeMarginMillis;

	public PlatformLogics(Function<TowerMessageReceiver, StandardPlatformMessaging> messagingMaker,
			Consumer<AgentLifecycleListener> allConsumer, PlatformEventEmitter eventEmitter, long commsTimeout,
			long serviceTimeMarginMillis, String myName) {
		super(messagingMaker, allConsumer, commsTimeout);
		this.myName = myName;
		this.serviceTimeMarginMillis = serviceTimeMarginMillis;
		this.eventEmitter = eventEmitter;
	}

	public void setModel(PlatformInternals<B> internals) {
		this.internals = internals;
		for (final var b : getBays()) {
			b.addBayListener(rechargeListener);
		}
	}

	@Override
	protected PlatformStatusResponse onPlatformStatusRequest(PlatformStatusRequest p) {
		LOGGER.info("Arrived PlatformStatusRequest {}", p);
		return new PlatformStatusResponse(internals.getGeoCoord(), status());
	}

	private PlatformStatus status() {
		return new PlatformStatus(currentReadyPs, alarms(), convert(getBays()), automationSystem().getServiceTime());
	}

	private List<B> getBays() {
		return internals.getAutomationSystem().getPayloadBays();
	}

	@Override
	protected ReadyPsResponse onReadyPsRequest(ReadyPsRequest p) throws CannotReadyPayload {
		automationSystem().preparePayload(p.bayId(), () -> readyPsUpdated(p.psId()));
		return new ReadyPsResponse();
	}

	private void readyPsUpdated(Integer psId) {
		currentReadyPs = psId;
		sendStatusIndication(new PlatformStatusIndication(
				new PlatformStatus(currentReadyPs, alarms(), null, automationSystem().getServiceTime())));
	}

	@Override
	protected DroneLandedConfirm onDroneLandedIndication(DroneLandedIndication p)
			throws UnexpectedDroneExcpetion, CannotStartPsException {
		LOGGER.info("Arrived DroneLandedIndication {}", p);
		final int psId = p.psId();
		checkLandedDroneId(p.droneId());
		LOGGER.info("Starting pitstop on external system for PS {}", psId);
		ElUtils.process(automationSystem().startPitStop(() -> this.readyPsUpdated(null)),
				automationSystem().getServiceTime() + serviceTimeMarginMillis, r -> this.onDone(psId),
				this::onPsServiceError, this::onAutomationPsServiceTimeout);
		return new DroneLandedConfirm();
	}

	private void onDone(int psId) {
		LOGGER.info("PitStop done");
		sendPsCompletedIndication(new PsCompletedIndication(psId, myName));
	}

	private void onPsServiceError(Throwable t) {
		LOGGER.error("Failed Pistop Service", t);
		updateFlangeUnavailable(true);
	}

	private void onAutomationPsServiceTimeout() {
		LOGGER.error("Pistop Service went Timeout");
		updateFlangeUnavailable(true);
	}

	private void updateFlangeUnavailable(boolean unavailable) {
		flangeUnvavilable = unavailable;
		sendStatusIndication(new PlatformStatusIndication(status()));
		eventEmitter.emitFlangeUnavailable(unavailable);
	}

	private void checkLandedDroneId(final String expectedDrone) throws UnexpectedDroneExcpetion {
		final String landedDroneId = automationSystem().getLandedDroneId();
		if (landedDroneId == null)
			throw new UnexpectedDroneExcpetion("I was expecting drone " + expectedDrone + " but no drone has landed");
		if (!landedDroneId.equals(expectedDrone))
			throw new UnexpectedDroneExcpetion(
					"I was expecting drone " + expectedDrone + " but drone landed is " + landedDroneId);
	}

	@Override
	protected void onQuitResponse(PlatformQuitResponse r) {
		LOGGER.info("Arrived QuitResponse {}", r);
		final long millisToWait = El.now().until(r.lastServiceAt(), ChronoUnit.MILLIS);
		LOGGER.debug("Quitting in {} ms", millisToWait);
		El.setTimeout(millisToWait, () -> {
			LOGGER.debug("Completing quitFuture");
			quitFuture.complete(null);
		});
	}

	@Override
	protected void onQuitResponseError(Throwable t) {
		quitFuture.completeExceptionally(t);
	}

	@Override
	protected void onQuitRepsonseTimeout() {
		LOGGER.error("QuitRepsonse went Timeout");
		quitFuture.completeExceptionally(new TimeoutException());
	}

	@Override
	protected void onStatusConfirm(PlatformStatusConfirm r) {
		LOGGER.debug("Status Confirmed");
	}

	@Override
	protected void onStatusConfirmError(Throwable t) {
		LOGGER.error("StatusConfirm Error", t);
	}

	@Override
	protected void onStatusConfirmTimeout() {
		LOGGER.error("StatusConfirm went Timeout");
	}

	@Override
	protected void onPsCompletedConfirm(PsCompletedConfirm r) {
		LOGGER.debug("PsCompleted Confirmed");
	}

	@Override
	protected void onPsCompletedConfirmError(Throwable t) {
		LOGGER.error("PsCompleted Confirm Error", t);
	}

	@Override
	protected void onPsCompletedConfirmTimeout() {
		LOGGER.error("PsCompleted Confirm went Timeout");
	}

	@Override
	protected PsAbortConfirm onPsAbortIndication(PsAbortIndication p) {
		LOGGER.info("Aborted PS " + p.psId());
		automationSystem().abortPitStop(() -> this.readyPsUpdated(null));
		return new PsAbortConfirm();
	}

	private List<PayloadBay> convert(B payloadBay) {
		return convert(Collections.singletonList(payloadBay));
	}

	private List<PayloadBay> convert(List<B> payloadBays) {
		return payloadBays.stream().map(b -> this.convertBay(b)).collect(Collectors.toList());
	}

	private PayloadBay convertBay(Bay<?> b) {
		return new PayloadBay(b.getId(), convertPayload(b.getPayload(), b.getRestorationInstant()),
				b.getPrepareMillis());
	}

	private RestoringPayload convertPayload(Optional<Payload> payload, Instant willRestoreAt) {
		return payload.map(p -> new RestoringPayload(p.id, p.type, p.charge, willRestoreAt)).orElse(null);
	}

	protected void onRechargeComplete(B bay) {
		LOGGER.info("Recharge complete in bay {}", bay);
		final var s = new PlatformStatus(currentReadyPs, alarms(), convert(bay), automationSystem().getServiceTime());
		sendStatusIndication(new PlatformStatusIndication(s));
	}

	protected void onBayContentUpdate(B bay) {
		LOGGER.info("Content update in bay {}", bay);
		final var s = new PlatformStatus(currentReadyPs, alarms(), convert(bay), automationSystem().getServiceTime());
		sendStatusIndication(new PlatformStatusIndication(s));
	}

	private PlatformAlarms alarms() {
		return new PlatformAlarms(flangeUnvavilable, cylinderBusy);
	}

	private PlatformAutomationSystem<B> automationSystem() {
		return internals.getAutomationSystem();
	}

	@Override
	public CompletableFuture<Void> quit() {
		quitFuture = new CompletableFuture<Void>();
		sendQuitRequest(new PlatformQuitRequest(myName));
		return quitFuture;
	}

	@Override
	public void resetFlangeUnavailableAlarm() {
		updateFlangeUnavailable(false);
	}

	@Override
	protected AnomalyConfirm onAnomalyIndicated(AnomalyIndication p) {
		if (p.anomalies().contains(Anomaly.CYLINDER_BUSY))
			cylinderBusy = true;
		final var s = new PlatformStatus(currentReadyPs, alarms(), null, automationSystem().getServiceTime());
		sendStatusIndication(new PlatformStatusIndication(s));
		return new AnomalyConfirm();
	}
}
