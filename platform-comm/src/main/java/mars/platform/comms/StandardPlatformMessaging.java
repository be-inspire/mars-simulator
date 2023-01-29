package mars.platform.comms;

import java.util.function.Consumer;

import com.cellply.invosys.agent.AbstractResponder;
import com.cellply.invosys.agent.Agent;
import com.cellply.invosys.agent.CallContext;
import com.cellply.invosys.agent.OutgoingInvocation;

import eventloop.EventLoop.Event;
import mars.messages.AnomalyIndication;
import mars.messages.DroneLandedIndication;
import mars.messages.MessageNames;
import mars.messages.PlatformQuitRequest;
import mars.messages.PlatformQuitResponse;
import mars.messages.PlatformStatusConfirm;
import mars.messages.PlatformStatusIndication;
import mars.messages.PlatformStatusRequest;
import mars.messages.PsAbortIndication;
import mars.messages.PsCompletedConfirm;
import mars.messages.PsCompletedIndication;
import mars.messages.ReadyPsRequest;

public class StandardPlatformMessaging implements PlatformMessaging {
	private final TowerMessageReceiver messageReceiver;
	private final Consumer<Event> executor;
	private final AgentPlatformMessaging agentPlatformMessaging;

	public StandardPlatformMessaging(Consumer<Event> executor, Agent agent, TowerMessageReceiver messageReceiver) {
		this.messageReceiver = messageReceiver;
		this.executor = executor;
		this.agentPlatformMessaging = new AgentPlatformMessaging(agent);
		agent.addMethod(MessageNames.PS_ABORT_INDICATION, new PsAbortResponder());
		agent.addMethod(MessageNames.PLATFORM_STATUS_REQUEST, new PlatformStatusRequestResponder());
		agent.addMethod(MessageNames.READY_PS_REQUEST, new ReadyPsRequestResponder());
		agent.addMethod(MessageNames.DRONE_LANDED_INDICATION, new DroneLandedIndicationResponder());
		agent.addMethod(MessageNames.ANOMALY_INDICATION, new AnomalyIndicationResponder());
	}

	public class PsAbortResponder extends AbstractResponder<PsAbortIndication> {

		public PsAbortResponder() {
			super(PsAbortIndication.class);
		}

		@Override
		public void executeInvocation(CallContext<PsAbortIndication> callCtx) {
			executor.accept(() -> messageReceiver.onPsAbort(callCtx));
		}
	}

	public class PlatformStatusRequestResponder extends AbstractResponder<PlatformStatusRequest> {

		public PlatformStatusRequestResponder() {
			super(PlatformStatusRequest.class);
		}

		@Override
		public void executeInvocation(CallContext<PlatformStatusRequest> callCtx) {
			executor.accept(() -> messageReceiver.onPlatformStatus(callCtx));
		}
	}

	public class ReadyPsRequestResponder extends AbstractResponder<ReadyPsRequest> {

		public ReadyPsRequestResponder() {
			super(ReadyPsRequest.class);
		}

		@Override
		public void executeInvocation(CallContext<ReadyPsRequest> callCtx) {
			executor.accept(() -> messageReceiver.onReadyPs(callCtx));
		}
	}

	public class DroneLandedIndicationResponder extends AbstractResponder<DroneLandedIndication> {

		public DroneLandedIndicationResponder() {
			super(DroneLandedIndication.class);
		}

		@Override
		public void executeInvocation(CallContext<DroneLandedIndication> callCtx) {
			executor.accept(() -> messageReceiver.onDroneLanded(callCtx));
		}
	}

	public class AnomalyIndicationResponder extends AbstractResponder<AnomalyIndication> {

		public AnomalyIndicationResponder() {
			super(AnomalyIndication.class);
		}

		@Override
		public void executeInvocation(CallContext<AnomalyIndication> callCtx) {
			executor.accept(() -> messageReceiver.onAnomalyIndicated(callCtx));
		}
	}

	@Override
	public OutgoingInvocation<PlatformStatusConfirm> sendPlatformStatusIndication(
			PlatformStatusIndication platformStatusIndication) {
		return agentPlatformMessaging.sendPlatformStatusIndication(platformStatusIndication);
	}

	@Override
	public OutgoingInvocation<PsCompletedConfirm> sendPsCompletedIndication(PsCompletedIndication psCompletedIndication) {
		return agentPlatformMessaging.sendPsCompletedIndication(psCompletedIndication);
	}

	@Override
	public OutgoingInvocation<PlatformQuitResponse> sendPlatformQuitRequest(PlatformQuitRequest platformQuitRequest) {
		return agentPlatformMessaging.sendPlatformQuitRequest(platformQuitRequest);
	}

	public void setTowerName(String towerName) {
		agentPlatformMessaging.setTowerName(towerName);
	}
}
