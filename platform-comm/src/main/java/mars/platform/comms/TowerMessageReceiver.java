package mars.platform.comms;

import com.cellply.invosys.agent.CallContext;

import mars.messages.AnomalyIndication;
import mars.messages.DroneLandedIndication;
import mars.messages.PlatformStatusRequest;
import mars.messages.PsAbortIndication;
import mars.messages.ReadyPsRequest;

public interface TowerMessageReceiver {

	void onPsAbort(CallContext<PsAbortIndication> callCtx);

	void onPlatformStatus(CallContext<PlatformStatusRequest> callCtx);

	void onReadyPs(CallContext<ReadyPsRequest> callCtx);

	void onDroneLanded(CallContext<DroneLandedIndication> callCtx);

	void onAnomalyIndicated(CallContext<AnomalyIndication> callCtx);
}
