package mars.tower.comms;

import java.util.function.Consumer;

import com.cellply.invosys.agent.AbstractResponder;
import com.cellply.invosys.agent.Agent;
import com.cellply.invosys.agent.CallContext;
import com.cellply.invosys.agent.OutgoingInvocation;

import eventloop.EventLoop.Event;
import mars.messages.AnomalyConfirm;
import mars.messages.AnomalyIndication;
import mars.messages.DroneLandedConfirm;
import mars.messages.DroneLandedIndication;
import mars.messages.MessageNames;
import mars.messages.PlatformAssignmentConfirm;
import mars.messages.PlatformAssignmentIndication;
import mars.messages.PlatformAvailabilityConfirm;
import mars.messages.PlatformAvailabilityIndication;
import mars.messages.PlatformCylinderEnterRequest;
import mars.messages.PlatformCylinderLeftIndication;
import mars.messages.PlatformQuitRequest;
import mars.messages.PlatformReachabilityIndication;
import mars.messages.PlatformStatusIndication;
import mars.messages.PlatformStatusRequest;
import mars.messages.PlatformStatusResponse;
import mars.messages.PsAbortConfirm;
import mars.messages.PsAbortIndication;
import mars.messages.PsCompletedConfirm;
import mars.messages.PsCompletedIndication;
import mars.messages.PsDemandRequest;
import mars.messages.ReadyPsRequest;
import mars.messages.ReadyPsResponse;
import mars.tower.TowerMessaging;

public class StandardTowerMessaging implements TowerMessaging {

	private final Agent agent;
	private final PlatformMessageReceiver platformMessageReceiver;
	private final McMessageReceiver mcMessageReceiver;
	private final Consumer<Event> executor;

	public StandardTowerMessaging(Consumer<Event> executor, Agent agent,
			PlatformMessageReceiver platformMessageReceiver, McMessageReceiver mcMessageReceiver) {
		this.agent = agent;
		this.platformMessageReceiver = platformMessageReceiver;
		this.mcMessageReceiver = mcMessageReceiver;
		this.executor = executor;

		// From MC
		agent.addMethod(MessageNames.PS_DEMAND_REQUEST, new PsDemandRequestResponder());
		agent.addMethod(MessageNames.PS_ABORT_INDICATION, new PsAbortIndicationResponder());
		agent.addMethod(MessageNames.PLATFORM_CYLINDER_ENTER_REQUEST, new PlatformCylinderEnterResponder());
		agent.addMethod(MessageNames.DRONE_LANDED_INDICATION, new DroneLandedIndicationResponder());
		agent.addMethod(MessageNames.PLATFORM_CYLINDER_LEFT_INDICATION, new PlatformCylinderLeftIndicationResponder());
		agent.addMethod(MessageNames.PLATFORM_REACHABILITY_INDICATION, new PlatformReachabilityIndicationResponder());
		// From P
		agent.addMethod(MessageNames.PLATFORM_STATUS_INDICATION, new PlatformStatusIndicationResponder());
		agent.addMethod(MessageNames.PLATFORM_QUIT_REQUEST, new PlatformQuitRequestResponder());
		agent.addMethod(MessageNames.PS_COMPLETED_INDICATION, new PsCompletedIndicationResponder());
	}

	public class PlatformStatusIndicationResponder extends AbstractResponder<PlatformStatusIndication> {

		public PlatformStatusIndicationResponder() {
			super(PlatformStatusIndication.class);
		}

		@Override
		public void executeInvocation(CallContext<PlatformStatusIndication> callCtx) {
			executor.accept(() -> platformMessageReceiver.onPlatformStatus(callCtx));
		}
	}

	public class PlatformQuitRequestResponder extends AbstractResponder<PlatformQuitRequest> {

		public PlatformQuitRequestResponder() {
			super(PlatformQuitRequest.class);
		}

		@Override
		public void executeInvocation(CallContext<PlatformQuitRequest> callCtx) {
			executor.accept(() -> platformMessageReceiver.onPlatformQuit(callCtx));
		}
	}

	public class PsCompletedIndicationResponder extends AbstractResponder<PsCompletedIndication> {

		public PsCompletedIndicationResponder() {
			super(PsCompletedIndication.class);
		}

		@Override
		public void executeInvocation(CallContext<PsCompletedIndication> callCtx) {
			executor.accept(() -> platformMessageReceiver.onPsCompleted(callCtx));
		}
	}

	public class PsDemandRequestResponder extends AbstractResponder<PsDemandRequest> {

		public PsDemandRequestResponder() {
			super(PsDemandRequest.class);
		}

		@Override
		public void executeInvocation(CallContext<PsDemandRequest> callCtx) {
			executor.accept(() -> mcMessageReceiver.onPsDemand(callCtx));
		}
	}

	public class PsAbortIndicationResponder extends AbstractResponder<PsAbortIndication> {

		public PsAbortIndicationResponder() {
			super(PsAbortIndication.class);
		}

		@Override
		public void executeInvocation(CallContext<PsAbortIndication> callCtx) {
			executor.accept(() -> mcMessageReceiver.onPsAbort(callCtx));
		}
	}

	public class PlatformCylinderEnterResponder extends AbstractResponder<PlatformCylinderEnterRequest> {

		public PlatformCylinderEnterResponder() {
			super(PlatformCylinderEnterRequest.class);
		}

		@Override
		public void executeInvocation(CallContext<PlatformCylinderEnterRequest> callCtx) {
			executor.accept(() -> mcMessageReceiver.onPlatformCylinderEnter(callCtx));
		}
	}

	public class DroneLandedIndicationResponder extends AbstractResponder<DroneLandedIndication> {

		public DroneLandedIndicationResponder() {
			super(DroneLandedIndication.class);
		}

		@Override
		public void executeInvocation(CallContext<DroneLandedIndication> callCtx) {
			executor.accept(() -> mcMessageReceiver.onDroneLanded(callCtx));
		}
	}

	public class PlatformCylinderLeftIndicationResponder extends AbstractResponder<PlatformCylinderLeftIndication> {

		public PlatformCylinderLeftIndicationResponder() {
			super(PlatformCylinderLeftIndication.class);
		}

		@Override
		public void executeInvocation(CallContext<PlatformCylinderLeftIndication> callCtx) {
			executor.accept(() -> mcMessageReceiver.onPlatformCylinderLeft(callCtx));
		}
	}

	public class PlatformReachabilityIndicationResponder extends AbstractResponder<PlatformReachabilityIndication> {

		public PlatformReachabilityIndicationResponder() {
			super(PlatformReachabilityIndication.class);
		}

		@Override
		public void executeInvocation(CallContext<PlatformReachabilityIndication> callCtx) {
			executor.accept(() -> mcMessageReceiver.onPlatformReachability(callCtx));
		}
	}

	@Override
	public OutgoingInvocation<PsAbortConfirm> sendPsAbortIndication(String agentName, PsAbortIndication psAbort) {
		return agent.getInvocationContext().invoke(PsAbortConfirm.class, agentName, MessageNames.PS_ABORT_INDICATION,
				psAbort);
	}

	@Override
	public OutgoingInvocation<PlatformStatusResponse> sendPlatformStatusRequest(String agentName,
			PlatformStatusRequest platformStatusRequest) {
		return agent.getInvocationContext().invoke(PlatformStatusResponse.class, agentName,
				MessageNames.PLATFORM_STATUS_REQUEST, platformStatusRequest);
	}

	@Override
	public OutgoingInvocation<ReadyPsResponse> sendReadyPsRequest(String agentName, ReadyPsRequest readyPsRequest) {
		return agent.getInvocationContext().invoke(ReadyPsResponse.class, agentName, MessageNames.READY_PS_REQUEST,
				readyPsRequest);
	}

	@Override
	public OutgoingInvocation<DroneLandedConfirm> sendDroneLandedIndication(String agentName,
			DroneLandedIndication droneLandedIndication) {
		return agent.getInvocationContext().invoke(DroneLandedConfirm.class, agentName,
				MessageNames.DRONE_LANDED_INDICATION, droneLandedIndication);
	}

	@Override
	public OutgoingInvocation<PsCompletedConfirm> sendPsCompletedIndication(String agentName,
			PsCompletedIndication psCompletedIndication) {
		return agent.getInvocationContext().invoke(PsCompletedConfirm.class, agentName,
				MessageNames.PS_COMPLETED_INDICATION, psCompletedIndication);
	}

	@Override
	public OutgoingInvocation<PlatformAssignmentConfirm> sendPlatformAssignmentIndication(String agentName,
			PlatformAssignmentIndication platformAssignmentIndication) {
		return agent.getInvocationContext().invoke(PlatformAssignmentConfirm.class, agentName,
				MessageNames.PLATFORM_ASSIGNMENT_INDICATION, platformAssignmentIndication);
	}

	@Override
	public OutgoingInvocation<PlatformAvailabilityConfirm> sendPlatformAvailabilityIndication(String agentName,
			PlatformAvailabilityIndication platformAvailabilityIndication) {
		return agent.getInvocationContext().invoke(PlatformAvailabilityConfirm.class, agentName,
				MessageNames.PLATFORM_AVAILABILITY_INDICATION, platformAvailabilityIndication);
	}

	@Override
	public OutgoingInvocation<AnomalyConfirm> sendAnomalyIndication(String destAgentName, AnomalyIndication ind) {
		return agent.getInvocationContext().invoke(AnomalyConfirm.class, destAgentName, MessageNames.ANOMALY_INDICATION,
				ind);
	}

}