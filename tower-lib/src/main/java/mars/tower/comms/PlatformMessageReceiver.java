package mars.tower.comms;

import com.cellply.invosys.agent.CallContext;

import mars.messages.PlatformQuitRequest;
import mars.messages.PlatformStatusIndication;
import mars.messages.PsCompletedIndication;

public interface PlatformMessageReceiver {

	void onPlatformStatus(CallContext<PlatformStatusIndication> callCtx);

	void onPsCompleted(CallContext<PsCompletedIndication> callCtx);

	void onPlatformQuit(CallContext<PlatformQuitRequest> callCtx);

}
