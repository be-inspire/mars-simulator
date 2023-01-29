package mars.platform.comms;

import com.cellply.invosys.agent.OutgoingInvocation;

import mars.messages.PlatformQuitRequest;
import mars.messages.PlatformQuitResponse;
import mars.messages.PlatformStatusConfirm;
import mars.messages.PlatformStatusIndication;
import mars.messages.PsCompletedConfirm;
import mars.messages.PsCompletedIndication;

public interface PlatformMessaging {

	PlatformMessaging NULL = new PlatformMessaging() {
		public static final String TOWER_IS_NOT_AVAILABLE = "TOWER is not available";

		@Override
		public OutgoingInvocation<PsCompletedConfirm> sendPsCompletedIndication(
				PsCompletedIndication psCompletedIndication) {
			throw new RuntimeException(TOWER_IS_NOT_AVAILABLE);
		}

		@Override
		public OutgoingInvocation<PlatformStatusConfirm> sendPlatformStatusIndication(
				PlatformStatusIndication platformStatusIndication) {
			throw new RuntimeException(TOWER_IS_NOT_AVAILABLE);
		}

		@Override
		public OutgoingInvocation<PlatformQuitResponse> sendPlatformQuitRequest(PlatformQuitRequest platformQuitRequest) {
			throw new RuntimeException(TOWER_IS_NOT_AVAILABLE);
		}
	};

	OutgoingInvocation<PlatformStatusConfirm> sendPlatformStatusIndication(
			PlatformStatusIndication platformStatusIndication);

	OutgoingInvocation<PsCompletedConfirm> sendPsCompletedIndication(PsCompletedIndication psCompletedIndication);

	OutgoingInvocation<PlatformQuitResponse> sendPlatformQuitRequest(PlatformQuitRequest platformQuitRequest);

}
