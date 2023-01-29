package mars.tower;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cellply.invosys.agent.CallContext;

import eventloop.ElUtils;
import mars.heartbeat.AgentLifecycleListener;
import mars.messages.AnomalyConfirm;
import mars.messages.AnomalyIndication;
import mars.messages.DroneLandedConfirm;
import mars.messages.DroneLandedIndication;
import mars.messages.HeartbeatIds;
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
import mars.messages.ReadyPsRequest;
import mars.messages.ReadyPsResponse;
import mars.messages.UnexpectedPsStateException;
import mars.tower.comms.McMessageReceiver;
import mars.tower.comms.PlatformMessageReceiver;

public abstract class AbstractLower<T> {

	// protected static final long TIMEOUT_MILLIS = 100000;

	private final long commsTimeoutTwr;

	protected final TowerMessaging messaging;
	protected final WorldModel worldModel;
	protected static final Logger LOGGER = LoggerFactory.getLogger(StandardLower.class);

	public AbstractLower(WorldModel worldModel,
			BiFunction<PlatformMessageReceiver, McMessageReceiver, TowerMessaging> messagingMaker,
			Consumer<AgentLifecycleListener> allConsumer, long commsTimeoutTwr) {
		this.worldModel = worldModel;
		this.commsTimeoutTwr = commsTimeoutTwr;
		this.messaging = messagingMaker.apply(platformMessageReceiver, mcMessageReceiver);
		allConsumer.accept(agentLifecycleListener);
	}

	//

	private final PlatformMessageReceiver platformMessageReceiver = new PlatformMessageReceiver() {

		@Override
		public void onPsCompleted(CallContext<PsCompletedIndication> callCtx) {
			final PsCompletedIndication params = callCtx.params();
			LOGGER.debug("<<- [{}]: {}", callCtx.callingAgentId(), callCtx.params());
			final CompletableFuture<PsCompletedConfirm> f = new CompletableFuture<>();
			f.whenComplete(callCtx.action());
			onPsCompletedIndication(params, f);
		}

		@Override
		public void onPlatformStatus(CallContext<PlatformStatusIndication> callCtx) {
			LOGGER.debug("<<- [{}]: {}", callCtx.callingAgentId(), callCtx.params());
			final String pltName = callCtx.callingAgentId();
			callCtx.exec((invCtx, p) -> {
				return onPlatformStatusIndication(pltName, p);
			});
		}

		@Override
		public void onPlatformQuit(CallContext<PlatformQuitRequest> callCtx) {
			LOGGER.debug("<<- [{}]: {}", callCtx.callingAgentId(), callCtx.params());
			final String pltName = callCtx.callingAgentId();
			callCtx.exec((invCtx, p) -> {
				return onPlatformQuitRequest(pltName, p);
			});

		}
	};

	protected abstract void onPsCompletedIndication(PsCompletedIndication p, CompletableFuture<PsCompletedConfirm> f);

	protected abstract PlatformStatusConfirm onPlatformStatusIndication(String pltName, PlatformStatusIndication p);

	protected abstract PlatformQuitResponse onPlatformQuitRequest(String pltName, PlatformQuitRequest p);

	//

	private final McMessageReceiver mcMessageReceiver = new McMessageReceiver() {

		@Override
		public void onPsDemand(CallContext<PsDemandRequest> callCtx) {
			LOGGER.debug("<<- [{}]: {}", callCtx.callingAgentId(), callCtx.params());
			final String mcId = callCtx.callingAgentId();
			callCtx.exec((invCtx, p) -> {
				return onPsDemandRequest(mcId, p);
			});
		}

		@Override
		public void onPlatformReachability(CallContext<PlatformReachabilityIndication> callCtx) {
			LOGGER.debug("<<- [{}]: {}", callCtx.callingAgentId(), callCtx.params());
			callCtx.exec((inv, p) -> {
				return onPlatformReachabilityIndication(p);
			});
		}

		@Override
		public void onPlatformCylinderEnter(CallContext<PlatformCylinderEnterRequest> callCtx) {
			final PlatformCylinderEnterRequest params = callCtx.params();
			LOGGER.debug("<<- [{}]: {}", callCtx.callingAgentId(), callCtx.params());
			final CompletableFuture<PlatformCylinderEnterResponse> f = new CompletableFuture<>();
			f.whenComplete(callCtx.action());
			onPlatformCylinderEnterRequest(params, f);
		}

		@Override
		public void onDroneLanded(CallContext<DroneLandedIndication> callCtx) {
			final DroneLandedIndication params = callCtx.params();
			LOGGER.debug("<<- [{}]: {}", callCtx.callingAgentId(), callCtx.params());
			final CompletableFuture<DroneLandedConfirm> f = new CompletableFuture<>();
			f.whenComplete(callCtx.action());
			onDroneLandedIndication(params, f);
		}

		@Override
		public void onPlatformCylinderLeft(CallContext<PlatformCylinderLeftIndication> callCtx) {
			LOGGER.debug("<<- [{}]: {}", callCtx.callingAgentId(), callCtx.params());
			callCtx.exec((inv, p) -> {
				return onPlatformCylinderLeftIndication(p);
			});
		}

		@Override
		public void onPsAbort(CallContext<PsAbortIndication> callCtx) {
			LOGGER.debug("<<- [{}]: {}", callCtx.callingAgentId(), callCtx.params());
			callCtx.exec((inv, p) -> {
				return onPsAbortIndication(p);
			});
		}
	};

	protected abstract PsDemandResponse onPsDemandRequest(String mcId, PsDemandRequest p)
			throws NoAvailabilityException;

	protected abstract PlatformReachabilityConfirm onPlatformReachabilityIndication(PlatformReachabilityIndication p);

	protected abstract void onPlatformCylinderEnterRequest(PlatformCylinderEnterRequest p,
			CompletableFuture<PlatformCylinderEnterResponse> f);

	protected abstract void onDroneLandedIndication(DroneLandedIndication p, CompletableFuture<DroneLandedConfirm> f);

	protected abstract PlatformCylinderLeftConfirm onPlatformCylinderLeftIndication(PlatformCylinderLeftIndication p);

	protected abstract PsAbortConfirm onPsAbortIndication(PsAbortIndication p) throws UnexpectedPsStateException;

	//

	private final AgentLifecycleListener agentLifecycleListener = new AgentLifecycleListener() {

		@Override
		public void agentReturned(String agentName, String category) {
			LOGGER.info("Agent returned: {} ({})" + agentName, category);
			if (HeartbeatIds.PLT.equals(category))
				onPlatformDiscovered(agentName);
		}

		@Override
		public void agentLost(String agentName, String category) {
			LOGGER.info("Agent lost: {} ({})", agentName, category);
			if (HeartbeatIds.PLT.equals(category))
				onPlatformLost(agentName);
		}

		@Override
		public void agentDiscovered(String agentName, String category) {
			LOGGER.info("Agent discovered: {} ({})", agentName, category);
			if (HeartbeatIds.PLT.equals(category))
				onPlatformDiscovered(agentName);
		}
	};

	protected abstract void onPlatformDiscovered(String agentName);

	protected abstract void onPlatformLost(String agentName);

	// Method from TOWER

	protected void sendPlatformAssignmentIndication(String destAgentName, T ps,
			final PlatformAssignmentIndication ind) {
		LOGGER.info("->> [{}] {}", destAgentName, ind);
		LOGGER.debug("For PS: {}", ps);
		ElUtils.process(messaging.sendPlatformAssignmentIndication(destAgentName, ind), commsTimeoutTwr,
				r -> onPlatformAssignmentConfirm(r, ps), t -> onPlatformAssignmentIndicationError(t, ps),
				() -> onPlatformAssignmentIndicationTimeout(ps));
	}

	protected abstract void onPlatformAssignmentConfirm(PlatformAssignmentConfirm r, T ps);

	protected abstract void onPlatformAssignmentIndicationError(Throwable t, T ps);

	protected abstract void onPlatformAssignmentIndicationTimeout(T ps);

	protected void sendReadyPsRequest(String destAgentName, ReadyPsRequest req, T ps) {
		LOGGER.info("->> [{}] {} ", destAgentName, req);
		LOGGER.debug("For PS: {}", ps);
		ElUtils.process(messaging.sendReadyPsRequest(destAgentName, req), commsTimeoutTwr,
				r -> onReadyPsResponse(destAgentName, r, ps), t -> onReadyPsRequestError(t, ps),
				() -> onReadyPsRequestTimeout(ps));
	}

	protected abstract void onReadyPsResponse(String destAgentName, ReadyPsResponse r, T ps);

	protected abstract void onReadyPsRequestError(Throwable t, T ps);

	protected abstract void onReadyPsRequestTimeout(T ps);

	protected void sendDroneLandedIndication(String destAgentName, DroneLandedIndication ind, T ps) {
		LOGGER.info("->> [{}] {}", destAgentName, ind);
		LOGGER.debug("For PS: {}", ps);
		ElUtils.process(messaging.sendDroneLandedIndication(destAgentName, ind), commsTimeoutTwr,
				r -> onDroneLandedConfirm(destAgentName, r, ps), t -> onDroneLandedIndicationError(t, ps),
				() -> onDroneLandedIndicationTimeout(ps));
	}

	protected abstract void onDroneLandedConfirm(String destAgentName, DroneLandedConfirm r, T ps);

	protected abstract void onDroneLandedIndicationError(Throwable t, T ps);

	protected abstract void onDroneLandedIndicationTimeout(T ps);

	protected void sendPsCompletedIndication(String destAgentName, PsCompletedIndication req, T ps) {
		LOGGER.info("->> [{}] {}", destAgentName, req);
		LOGGER.debug("For PS: {}", ps);
		ElUtils.process(messaging.sendPsCompletedIndication(destAgentName, req), commsTimeoutTwr,
				r -> onPsCompletedConfirm(destAgentName, r, ps), t -> onPsCompletedIndicationError(t, ps),
				() -> onPsCompletedIndicationTimeout(ps));
	}

	protected abstract void onPsCompletedConfirm(String destAgentName, PsCompletedConfirm r, T ps);

	protected abstract void onPsCompletedIndicationError(Throwable t, T ps);

	protected abstract void onPsCompletedIndicationTimeout(T ps);

	protected void sendPlatformStatusRequest(String destAgentName, PlatformStatusRequest req) {
		LOGGER.info("->> [{}] {}", destAgentName, req);
		ElUtils.process(messaging.sendPlatformStatusRequest(destAgentName, req), commsTimeoutTwr,
				r -> onPlatformStatusResponse(destAgentName, r), this::onPlatformStatusRequestError,
				this::onPlatformStatusRequestTimeout);
	}

	protected abstract void onPlatformStatusResponse(String pltName, PlatformStatusResponse r);

	protected abstract void onPlatformStatusRequestError(Throwable t);

	protected abstract void onPlatformStatusRequestTimeout();

	public void sendPsAbortToMc(String destAgentName, PsAbortIndication ind, T ps) {
		LOGGER.info("->> [{}] {}", destAgentName, ind);
		LOGGER.debug("For PS: {}", ps);
		ElUtils.process(messaging.sendPsAbortIndication(destAgentName, ind), commsTimeoutTwr,
				r -> onPsAbortToMcConfirm(destAgentName, r, ps), t -> onPsAbortToMcIndicationError(t, ps),
				() -> onPsAbortToMcIndicationTimeout(ps));
	}

	protected abstract void onPsAbortToMcConfirm(String destAgentName, PsAbortConfirm r, T ps);

	protected abstract void onPsAbortToMcIndicationError(Throwable t, T ps);

	protected abstract void onPsAbortToMcIndicationTimeout(T ps);

	protected void sendPsAbortToPlt(String destAgentName, PsAbortIndication ind, T ps) {
		LOGGER.info("->> [{}] {}", destAgentName, ind);
		LOGGER.debug("For PS: {}", ps);
		ElUtils.process(messaging.sendPsAbortIndication(destAgentName, ind), commsTimeoutTwr,
				r -> onPsAbortToPltConfirm(destAgentName, r, ps), t -> onPsAbortToPltIndicationError(t, ps),
				() -> onPsAbortToPltIndicationTimeout(ps));
	}

	protected abstract void onPsAbortToPltConfirm(String destAgentName, PsAbortConfirm r, T ps);

	protected abstract void onPsAbortToPltIndicationError(Throwable t, T ps);

	protected abstract void onPsAbortToPltIndicationTimeout(T ps);

	protected void sendAnomalyIndication(String destAgentName, AnomalyIndication ind, T ps) {
		LOGGER.info("->> [{}] {}", destAgentName, ind);
		LOGGER.debug("For PS: {}", ps);
		ElUtils.process(messaging.sendAnomalyIndication(destAgentName, ind), commsTimeoutTwr,
				r -> onAnomalyConfirm(destAgentName, r, ps), t -> onAnomalyIndicationError(t, ps),
				() -> onAnomalyIndicationTimeout(ps));
	}

	protected abstract void onAnomalyConfirm(String destAgentName, AnomalyConfirm r, T ps);

	protected abstract void onAnomalyIndicationError(Throwable t, T ps);

	protected abstract void onAnomalyIndicationTimeout(T ps);

}