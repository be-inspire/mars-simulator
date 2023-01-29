package mars.mc;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cellply.invosys.ComSystem;
import com.cellply.invosys.agent.CallContext;
import com.cellply.invosys.signalling.SignalEmitter;

import eventloop.ElUtils;
import mars.heartbeat.AgentLifecycleListener;
import mars.mc.comms.McMessaging;
import mars.mc.comms.TowerMessageReceiver;
import mars.messages.DroneLandedIndication;
import mars.messages.PlatformAssignmentIndication;
import mars.messages.PlatformAvailabilityIndication;
import mars.messages.PlatformCylinderEnterRequest;
import mars.messages.PlatformCylinderLeftIndication;
import mars.messages.PlatformReachabilityIndication;
import mars.messages.PsAbortIndication;
import mars.messages.PsCompletedIndication;
import mars.messages.PsDemandRequest;

/**
 * The implementation of both {@link McComms} and {@link McSideComms} on
 * {@link ComSystem}.
 * 
 * @author mperrando
 *
 * @param <T> the type of the PitStop.
 */
public class McCommsOnComSystem<T> implements McComms<T>, McSideComms {

	private final static Logger LOGGER = LoggerFactory.getLogger(McCommsOnComSystem.class);

	private final long commsTimeoutMs;

	private McMessaging mcMessaging = McMessaging.NULL;
	private final McMessaging activeMessaging;
	private final SignalEmitter signalEmitter;
	private McCommsListener<T> l;

	public McCommsOnComSystem(Function<TowerMessageReceiver, McMessaging> mcMessagingMaker,
			Consumer<AgentLifecycleListener> allConsumer, SignalEmitter signalEmitter, long commsTimeoutMs) {
		this.signalEmitter = signalEmitter;
		activeMessaging = mcMessagingMaker.apply(receiver);
		allConsumer.accept(new McLifecycleListener());
		this.commsTimeoutMs = commsTimeoutMs;
	}

	private final class McLifecycleListener implements AgentLifecycleListener {
		@Override
		public void agentReturned(String agentName, String category) {
			LOGGER.info("Agent returned: {} ({})", agentName, category);
			activeMessaging.setTowerName(agentName);
			mcMessaging = activeMessaging;
			l.onTowerReturned(agentName);
		}

		@Override
		public void agentLost(String agentName, String category) {
			LOGGER.info("Agent lost: {} ({})", agentName, category);
			mcMessaging = McMessaging.NULL;
			l.onTowerLost(agentName);
		}

		@Override
		public void agentDiscovered(String agentName, String category) {
			LOGGER.info("Agent discovered: {} ({})", agentName, category);
			activeMessaging.setTowerName(agentName);
			mcMessaging = activeMessaging;
			l.onTowerDiscovered(agentName);
		}
	}

	private final TowerMessageReceiver receiver = new TowerMessageReceiver() {

		@Override
		public void onPsCompleted(CallContext<PsCompletedIndication> callCtx) {
			callCtx.exec((inv, p) -> {
				return l.onPsCompletedIndication(p);
			});
		}

		@Override
		public void onPsAbort(CallContext<PsAbortIndication> callCtx) {
			callCtx.exec((inv, p) -> {
				return l.onPsAbortIndication(p);
			});
		}

		@Override
		public void onPlatformAssignment(CallContext<PlatformAssignmentIndication> callCtx) {
			callCtx.exec((inv, p) -> {
				return l.onPlatformAssignmentIndication(p);
			});
		}

		@Override
		public void onPlatformAvailability(CallContext<PlatformAvailabilityIndication> callCtx) {
			callCtx.exec((inv, p) -> {
				return l.onPlatformAvailabilityIndication(p);
			});
		}
	};

	@Override
	public void sendPlatformReachabilityIndication(T ps, final PlatformReachabilityIndication indication) {
		LOGGER.info("Sending PlatformReachabilityIndication: {}", indication);
		ElUtils.process(mcMessaging.sendPlatformReachabilityIndication(indication), commsTimeoutMs,
				r -> l.onPlatformReachabilityConfirm(ps), t -> l.onPlatformReachabilityError(t, ps),
				() -> l.onPlatformReachabilityTimeout(ps));
	}

	@Override
	public void sendPlatformCylinderEnterRequest(final PlatformCylinderEnterRequest req, T ps) {
		LOGGER.info("Sending PlatformCylinderEnterRequest: {}", req);
		ElUtils.process(mcMessaging.sendPlatformCylinderEnterRequest(req), commsTimeoutMs,
				r -> l.onCylinderEnterResponse(r, ps), t -> l.onCylinderEnterError(t, ps),
				() -> l.onCylinderEnterTimeout(ps));
	}

	@Override
	public void sendDroneLandedIndication(DroneLandedIndication indication, T ps) {
		LOGGER.info("Sending DroneLandedIndication: {}", indication);
		ElUtils.process(mcMessaging.sendDroneLandedIndication(indication), (long) 1000,
				r -> l.onDroneLandedConfirmed(r, ps), t -> l.onDroneLandedError(t, ps),
				() -> l.onDroneLandedTimeout(ps));
	}

	@Override
	public void sendPsDemandRequest(final PsDemandRequest psDemandRequest, T ps) {
		LOGGER.info("Sending PsDemandRequest: {}", psDemandRequest);
		ElUtils.process(mcMessaging.sendPsDemandRequest(psDemandRequest), commsTimeoutMs,
				r -> l.onPsDemandResponse(r, ps), t -> l.onPsDemandError(t, ps), () -> l.onPsDemandTimeout(ps));
	}

	@Override
	public void sendPlatformCylinderLeftIndication(PlatformCylinderLeftIndication indication, T ps) {
		LOGGER.info("Sending PlatformCylinderLeftIndication: {}", indication);
		ElUtils.process(mcMessaging.sendPlatformCylinderLeftIndication(indication), commsTimeoutMs,
				r -> l.onCylinderLeftConfirmed(r, ps), t -> l.onCylinderLeftError(t, ps),
				() -> l.onCylinderLeftTimeout(ps));
	}

	@Override
	public <S> void emitSideSignal(String name, String eventName, S payload) {
		LOGGER.info("Emitting side signal: {}[{}]({})", name, eventName, payload);
		try {
			signalEmitter.emit(name, eventName, payload);
		} catch (final IOException e) {
			LOGGER.error("Cannot emit side signal: {}[{}]({})", name, eventName, payload);
		}
	}

	@Override
	public void setListener(McCommsListener<T> l) {
		this.l = l;
	}

	@Override
	public boolean isTowerActive() {
		return mcMessaging == activeMessaging;
	}
}
