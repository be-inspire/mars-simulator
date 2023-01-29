package mars.platform.comms;

import com.cellply.invosys.agent.Agent;
import com.cellply.invosys.agent.InvocationContext;
import com.cellply.invosys.agent.OutgoingInvocation;

import mars.messages.MessageNames;
import mars.messages.PlatformQuitRequest;
import mars.messages.PlatformQuitResponse;
import mars.messages.PlatformStatusConfirm;
import mars.messages.PlatformStatusIndication;
import mars.messages.PsCompletedConfirm;
import mars.messages.PsCompletedIndication;

public class AgentPlatformMessaging implements PlatformMessaging {
	private final Agent agent;

	private String towerName;

	public AgentPlatformMessaging(Agent agent) {
		this.agent = agent;
	}

	@Override
	public OutgoingInvocation<PlatformStatusConfirm> sendPlatformStatusIndication(
			PlatformStatusIndication platformStatusIndication) {
		return ic().invoke(PlatformStatusConfirm.class, towerName, MessageNames.PLATFORM_STATUS_INDICATION,
				platformStatusIndication);
	}

	private InvocationContext ic() {
		if (towerName == null)
			throw new RuntimeException("TOWER is absent");
		return agent.getInvocationContext();
	}

	@Override
	public OutgoingInvocation<PsCompletedConfirm> sendPsCompletedIndication(PsCompletedIndication psCompletedIndication) {
		return ic().invoke(PsCompletedConfirm.class, towerName, MessageNames.PS_COMPLETED_INDICATION,
				psCompletedIndication);
	}

	@Override
	public OutgoingInvocation<PlatformQuitResponse> sendPlatformQuitRequest(PlatformQuitRequest platformQuitRequest) {
		return ic().invoke(PlatformQuitResponse.class, towerName, MessageNames.PLATFORM_QUIT_REQUEST,
				platformQuitRequest);
	}

	public void setTowerName(String towerName) {
		this.towerName = towerName;
	}

}
