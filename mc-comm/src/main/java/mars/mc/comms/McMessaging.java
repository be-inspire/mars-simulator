package mars.mc.comms;

import com.cellply.invosys.agent.OutgoingInvocation;

import mars.messages.DroneLandedConfirm;
import mars.messages.DroneLandedIndication;
import mars.messages.PlatformCylinderEnterRequest;
import mars.messages.PlatformCylinderEnterResponse;
import mars.messages.PlatformCylinderLeftConfirm;
import mars.messages.PlatformCylinderLeftIndication;
import mars.messages.PlatformReachabilityConfirm;
import mars.messages.PlatformReachabilityIndication;
import mars.messages.PsAbortConfirm;
import mars.messages.PsAbortIndication;
import mars.messages.PsDemandRequest;
import mars.messages.PsDemandResponse;

public interface McMessaging {

	McMessaging NULL = new McMessaging() {
		public static final String TOWER_IS_NOT_AVAILABLE = "TOWER is not available";

		@Override
		public OutgoingInvocation<PsDemandResponse> sendPsDemandRequest(PsDemandRequest psDemandRequest) {
			return OutgoingInvocation.failed(new RuntimeException(TOWER_IS_NOT_AVAILABLE));
		}

		@Override
		public OutgoingInvocation<PsAbortConfirm> sendPsAbortIndication(PsAbortIndication psAbortIndication) {
			return OutgoingInvocation.failed(new RuntimeException(TOWER_IS_NOT_AVAILABLE));
		}

		@Override
		public OutgoingInvocation<PlatformCylinderLeftConfirm> sendPlatformCylinderLeftIndication(
				PlatformCylinderLeftIndication platformCylinderLeftIndication) {
			return OutgoingInvocation.failed(new RuntimeException(TOWER_IS_NOT_AVAILABLE));
		}

		@Override
		public OutgoingInvocation<PlatformCylinderEnterResponse> sendPlatformCylinderEnterRequest(
				PlatformCylinderEnterRequest platformCylinderEnterRequest) {
			return OutgoingInvocation.failed(new RuntimeException(TOWER_IS_NOT_AVAILABLE));
		}

		@Override
		public OutgoingInvocation<PlatformReachabilityConfirm> sendPlatformReachabilityIndication(
				PlatformReachabilityIndication platformReachabilityIndication) {
			return OutgoingInvocation.failed(new RuntimeException(TOWER_IS_NOT_AVAILABLE));
		}

		@Override
		public OutgoingInvocation<DroneLandedConfirm> sendDroneLandedIndication(
				DroneLandedIndication droneLandedIndication) {
			return OutgoingInvocation.failed(new RuntimeException(TOWER_IS_NOT_AVAILABLE));
		}

		@Override
		public void setTowerName(String agentName) {
		}
	};

	// 2.1.1 - 2.1.2
	OutgoingInvocation<PsDemandResponse> sendPsDemandRequest(PsDemandRequest psDemandRequest);

	OutgoingInvocation<PsAbortConfirm> sendPsAbortIndication(PsAbortIndication psAbortIndication);

	OutgoingInvocation<PlatformCylinderEnterResponse> sendPlatformCylinderEnterRequest(
			PlatformCylinderEnterRequest platformCylinderEnterRequest);

	OutgoingInvocation<DroneLandedConfirm> sendDroneLandedIndication(DroneLandedIndication droneLandedIndication);

	OutgoingInvocation<PlatformCylinderLeftConfirm> sendPlatformCylinderLeftIndication(
			PlatformCylinderLeftIndication platformCylinderLeftIndication);

	OutgoingInvocation<PlatformReachabilityConfirm> sendPlatformReachabilityIndication(
			PlatformReachabilityIndication platformReachabilityIndication);

	void setTowerName(String agentName);

}
