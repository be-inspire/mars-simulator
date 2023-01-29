package mars.tower;

import com.cellply.invosys.agent.OutgoingInvocation;

import mars.messages.AnomalyConfirm;
import mars.messages.AnomalyIndication;
import mars.messages.DroneLandedConfirm;
import mars.messages.DroneLandedIndication;
import mars.messages.PlatformAssignmentConfirm;
import mars.messages.PlatformAssignmentIndication;
import mars.messages.PlatformAvailabilityConfirm;
import mars.messages.PlatformAvailabilityIndication;
import mars.messages.PlatformStatusRequest;
import mars.messages.PlatformStatusResponse;
import mars.messages.PsAbortConfirm;
import mars.messages.PsAbortIndication;
import mars.messages.PsCompletedConfirm;
import mars.messages.PsCompletedIndication;
import mars.messages.ReadyPsRequest;
import mars.messages.ReadyPsResponse;

public interface TowerMessaging {

	OutgoingInvocation<PsAbortConfirm> sendPsAbortIndication(String destAgentName, PsAbortIndication psAbort);

	OutgoingInvocation<PlatformStatusResponse> sendPlatformStatusRequest(String destAgentName,
			PlatformStatusRequest platformStatusRequest);

	OutgoingInvocation<ReadyPsResponse> sendReadyPsRequest(String destAgentName, ReadyPsRequest readyPsRequest);

	OutgoingInvocation<DroneLandedConfirm> sendDroneLandedIndication(String destAgentName,
			DroneLandedIndication droneLandedIndication);

	OutgoingInvocation<PsCompletedConfirm> sendPsCompletedIndication(String destAgentName,
			PsCompletedIndication psCompletedIndication);

	OutgoingInvocation<PlatformAssignmentConfirm> sendPlatformAssignmentIndication(String destAgentName,
			PlatformAssignmentIndication platformAssignmentIndication);

	OutgoingInvocation<PlatformAvailabilityConfirm> sendPlatformAvailabilityIndication(String destAgentName,
			PlatformAvailabilityIndication platformAvailabilityIndication);

	OutgoingInvocation<AnomalyConfirm> sendAnomalyIndication(String destAgentName, AnomalyIndication ind);

}
