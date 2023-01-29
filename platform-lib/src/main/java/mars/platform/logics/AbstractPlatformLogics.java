package mars.platform.logics;

import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cellply.invosys.agent.CallContext;

import eventloop.ElUtils;
import mars.heartbeat.AgentLifecycleListener;
import mars.messages.AnomalyConfirm;
import mars.messages.AnomalyIndication;
import mars.messages.CannotReadyPayload;
import mars.messages.CannotStartPsException;
import mars.messages.DroneLandedConfirm;
import mars.messages.DroneLandedIndication;
import mars.messages.PlatformQuitRequest;
import mars.messages.PlatformQuitResponse;
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
import mars.messages.UnexpectedDroneExcpetion;
import mars.platform.comms.StandardPlatformMessaging;
import mars.platform.comms.TowerMessageReceiver;

public abstract class AbstractPlatformLogics {

	private final static Logger LOGGER = LoggerFactory.getLogger(AbstractPlatformLogics.class);
	private final StandardPlatformMessaging platformMessaging;

	private final long commsTimeout;

	public AbstractPlatformLogics(Function<TowerMessageReceiver, StandardPlatformMessaging> messagingMaker,
			Consumer<AgentLifecycleListener> allConsumer, long commsTimeout) {
		platformMessaging = messagingMaker.apply(messageReceiver);
		allConsumer.accept(agentLifecycleListener);
		this.commsTimeout = commsTimeout;
	}

	private final TowerMessageReceiver messageReceiver = new TowerMessageReceiver() {

		@Override
		public void onReadyPs(CallContext<ReadyPsRequest> callCtx) {
			callCtx.exec((invCtx, p) -> {
				return onReadyPsRequest(p);
			});
		}

		@Override
		public void onPlatformStatus(CallContext<PlatformStatusRequest> callCtx) {
			callCtx.exec((invCtx, p) -> {
				return onPlatformStatusRequest(p);
			});
		}

		@Override
		public void onDroneLanded(CallContext<DroneLandedIndication> callCtx) {
			callCtx.exec((invCtx, p) -> {
				return onDroneLandedIndication(p);
			});
		}

		@Override
		public void onPsAbort(CallContext<PsAbortIndication> callCtx) {
			callCtx.exec((inv, p) -> {
				return onPsAbortIndication(p);
			});
		}

		@Override
		public void onAnomalyIndicated(CallContext<AnomalyIndication> callCtx) {
			callCtx.exec((inv, p) -> {
				return AbstractPlatformLogics.this.onAnomalyIndicated(p);
			});
		}

	};

	protected abstract PsAbortConfirm onPsAbortIndication(PsAbortIndication p);

	protected abstract AnomalyConfirm onAnomalyIndicated(AnomalyIndication p);

	private final AgentLifecycleListener agentLifecycleListener = new AgentLifecycleListener() {

		@Override
		public void agentReturned(String agentName, String category) {
			LOGGER.info("Agent returned: {} ({})", agentName, category);
			platformMessaging.setTowerName(agentName);
		}

		@Override
		public void agentLost(String agentName, String category) {
			LOGGER.info("Agent lost: {} ({})", agentName, category);
			platformMessaging.setTowerName(null);
		}

		@Override
		public void agentDiscovered(String agentName, String category) {
			LOGGER.info("Agent discovered: {} ({})", agentName, category);
			platformMessaging.setTowerName(agentName);
		}
	};

	protected abstract PlatformStatusResponse onPlatformStatusRequest(PlatformStatusRequest p);

	protected abstract ReadyPsResponse onReadyPsRequest(ReadyPsRequest p) throws CannotReadyPayload;

	protected abstract DroneLandedConfirm onDroneLandedIndication(DroneLandedIndication p)
			throws UnexpectedDroneExcpetion, CannotStartPsException;

	protected void sendQuitRequest(PlatformQuitRequest platformQuitRequest) {
		LOGGER.info("Sending QuitRequest {}", platformQuitRequest);
		ElUtils.process(platformMessaging.sendPlatformQuitRequest(platformQuitRequest), commsTimeout,
				r -> this.onQuitResponse(r), t -> this.onQuitResponseError(t), () -> this.onQuitRepsonseTimeout());
	}

	protected abstract void onQuitResponse(PlatformQuitResponse r);

	protected abstract void onQuitResponseError(Throwable t);

	protected abstract void onQuitRepsonseTimeout();

	protected void sendStatusIndication(PlatformStatusIndication statusIndication) {
		LOGGER.info("Sending StatusIndication {}", statusIndication);
		ElUtils.process(platformMessaging.sendPlatformStatusIndication(statusIndication), commsTimeout,
				r -> this.onStatusConfirm(r), t -> this.onStatusConfirmError(t), () -> this.onStatusConfirmTimeout());
	}

	protected abstract void onStatusConfirm(PlatformStatusConfirm r);

	protected abstract void onStatusConfirmError(Throwable t);

	protected abstract void onStatusConfirmTimeout();

	protected void sendPsCompletedIndication(PsCompletedIndication psCompletedIndication) {
		LOGGER.info("Sending PsCompletedIndication {}", psCompletedIndication);
		ElUtils.process(platformMessaging.sendPsCompletedIndication(psCompletedIndication), commsTimeout,
				r -> this.onPsCompletedConfirm(r), t -> this.onPsCompletedConfirmError(t),
				() -> this.onPsCompletedConfirmTimeout());
	}

	protected abstract void onPsCompletedConfirm(PsCompletedConfirm r);

	protected abstract void onPsCompletedConfirmError(Throwable t);

	protected abstract void onPsCompletedConfirmTimeout();

}
