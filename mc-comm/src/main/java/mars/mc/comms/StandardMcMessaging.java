package mars.mc.comms;

import java.util.function.Consumer;

import com.cellply.invosys.agent.AbstractResponder;
import com.cellply.invosys.agent.Agent;
import com.cellply.invosys.agent.CallContext;
import com.cellply.invosys.agent.OutgoingInvocation;

import eventloop.EventLoop.Event;
import mars.messages.DroneLandedConfirm;
import mars.messages.DroneLandedIndication;
import mars.messages.MessageNames;
import mars.messages.PlatformAssignmentIndication;
import mars.messages.PlatformAvailabilityIndication;
import mars.messages.PlatformCylinderEnterRequest;
import mars.messages.PlatformCylinderEnterResponse;
import mars.messages.PlatformCylinderLeftConfirm;
import mars.messages.PlatformCylinderLeftIndication;
import mars.messages.PlatformReachabilityConfirm;
import mars.messages.PlatformReachabilityIndication;
import mars.messages.PsAbortConfirm;
import mars.messages.PsAbortIndication;
import mars.messages.PsCompletedIndication;
import mars.messages.PsDemandRequest;
import mars.messages.PsDemandResponse;

public class StandardMcMessaging implements McMessaging {

	private final TowerMessageReceiver messageReceiver;
	private final Agent agent;
	private final Consumer<Event> executor;

	private String towerName;

	public StandardMcMessaging(Consumer<Event> executor, Agent agent, TowerMessageReceiver messageReceiver) {
		this.agent = agent;
		this.messageReceiver = messageReceiver;
		this.executor = executor;
		agent.addMethod(MessageNames.PS_ABORT_INDICATION, new PsAbortResponder());
		agent.addMethod(MessageNames.PS_COMPLETED_INDICATION, new PsCompletedIndicationResponder());
		agent.addMethod(MessageNames.PLATFORM_ASSIGNMENT_INDICATION, new PlatformAssignmentIndicationResponder());
		agent.addMethod(MessageNames.PLATFORM_AVAILABILITY_INDICATION, new PlatformAvailabilityIndicationResponder());
	}

	public String getTowerName() {
		return towerName;
	}

	@Override
	public void setTowerName(String towerName) {
		this.towerName = towerName;
	}

	public class PsCompletedIndicationResponder extends AbstractResponder<PsCompletedIndication> {

		public PsCompletedIndicationResponder() {
			super(PsCompletedIndication.class);
		}

		@Override
		public void executeInvocation(CallContext<PsCompletedIndication> callCtx) {
			executor.accept(() -> messageReceiver.onPsCompleted(callCtx));
		}
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

	public class PlatformAssignmentIndicationResponder extends AbstractResponder<PlatformAssignmentIndication> {

		public PlatformAssignmentIndicationResponder() {
			super(PlatformAssignmentIndication.class);
		}

		@Override
		public void executeInvocation(CallContext<PlatformAssignmentIndication> callCtx) {
			executor.accept(() -> messageReceiver.onPlatformAssignment(callCtx));
		}
	}

	public class PlatformAvailabilityIndicationResponder extends AbstractResponder<PlatformAvailabilityIndication> {

		public PlatformAvailabilityIndicationResponder() {
			super(PlatformAvailabilityIndication.class);
		}

		@Override
		public void executeInvocation(CallContext<PlatformAvailabilityIndication> callCtx) {
			executor.accept(() -> messageReceiver.onPlatformAvailability(callCtx));
		}
	}

	@Override
	public OutgoingInvocation<PsDemandResponse> sendPsDemandRequest(PsDemandRequest psRequirement) {
		return agent.getInvocationContext().invoke(PsDemandResponse.class, towerName, MessageNames.PS_DEMAND_REQUEST,
				psRequirement);
	}

	@Override
	public OutgoingInvocation<PsAbortConfirm> sendPsAbortIndication(PsAbortIndication psAbort) {
		return agent.getInvocationContext().invoke(PsAbortConfirm.class, towerName, MessageNames.PS_ABORT_INDICATION,
				psAbort);
	}

	@Override
	public OutgoingInvocation<PlatformCylinderEnterResponse> sendPlatformCylinderEnterRequest(
			PlatformCylinderEnterRequest platformCylinderEnterRequest) {
		return agent.getInvocationContext().invoke(PlatformCylinderEnterResponse.class, towerName,
				MessageNames.PLATFORM_CYLINDER_ENTER_REQUEST, platformCylinderEnterRequest);
	}

	@Override
	public OutgoingInvocation<DroneLandedConfirm> sendDroneLandedIndication(DroneLandedIndication droneLandedIndication) {
		return agent.getInvocationContext().invoke(DroneLandedConfirm.class, towerName,
				MessageNames.DRONE_LANDED_INDICATION, droneLandedIndication);
	}

	@Override
	public OutgoingInvocation<PlatformCylinderLeftConfirm> sendPlatformCylinderLeftIndication(
			PlatformCylinderLeftIndication platformCylinderLeftIndication) {
		return agent.getInvocationContext().invoke(PlatformCylinderLeftConfirm.class, towerName,
				MessageNames.PLATFORM_CYLINDER_LEFT_INDICATION, platformCylinderLeftIndication);
	}

	@Override
	public OutgoingInvocation<PlatformReachabilityConfirm> sendPlatformReachabilityIndication(
			PlatformReachabilityIndication platformReachabilityIndication) {
		return agent.getInvocationContext().invoke(PlatformReachabilityConfirm.class, towerName,
				MessageNames.PLATFORM_REACHABILITY_INDICATION, platformReachabilityIndication);
	}
}
