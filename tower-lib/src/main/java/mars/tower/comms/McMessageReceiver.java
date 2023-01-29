package mars.tower.comms;

import com.cellply.invosys.agent.CallContext;

import mars.messages.DroneLandedIndication;
import mars.messages.PlatformCylinderEnterRequest;
import mars.messages.PlatformCylinderLeftIndication;
import mars.messages.PlatformReachabilityIndication;
import mars.messages.PsAbortIndication;
import mars.messages.PsDemandRequest;

public interface McMessageReceiver {

	void onPsDemand(CallContext<PsDemandRequest> callCtx);

	void onPsAbort(CallContext<PsAbortIndication> callCtx);

	void onPlatformCylinderEnter(CallContext<PlatformCylinderEnterRequest> callCtx);

	void onDroneLanded(CallContext<DroneLandedIndication> callCtx);

	void onPlatformCylinderLeft(CallContext<PlatformCylinderLeftIndication> callCtx);

	void onPlatformReachability(CallContext<PlatformReachabilityIndication> callCtx);

}
