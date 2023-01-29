package mars.mc.comms;

import com.cellply.invosys.agent.CallContext;

import mars.messages.PlatformAssignmentIndication;
import mars.messages.PlatformAvailabilityIndication;
import mars.messages.PsAbortIndication;
import mars.messages.PsCompletedIndication;

public interface TowerMessageReceiver {

	void onPsAbort(CallContext<PsAbortIndication> callCtx);

	void onPsCompleted(CallContext<PsCompletedIndication> callCtx);

	void onPlatformAssignment(CallContext<PlatformAssignmentIndication> callCtx);

	void onPlatformAvailability(CallContext<PlatformAvailabilityIndication> callCtx);

}
