package mars.tower.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.Arrays;
import java.util.Optional;

import mars.messages.PsDemandRequest;
import mars.messages.PsPlatformReachability;
import mars.messages.PsState;
import mars.tower.Assignation;
import mars.tower.PitStop;

public class PitstopChecker {

	private final PitStop pitStop;

	public PitstopChecker(PitStop pitStop) {
		this.pitStop = pitStop;
	}

	public PitstopChecker hasState(PsState state) {
		assertEquals(state, pitStop.getState(), pitStop.toString());
		return this;
	}

	public PitstopChecker hasReachabilites(PsPlatformReachability... reachabilities) {
		assertIterableEquals(Arrays.asList(reachabilities), pitStop.getReachabilities(), pitStop.toString());
		return this;
	}

	public PitstopChecker hasReqeust(PsDemandRequest psDemandRequest) {
		assertEquals(psDemandRequest, pitStop.request, pitStop.toString());
		return this;
	}

	public PitstopChecker hasMcId(String mcId) {
		assertEquals(mcId, pitStop.mcId, pitStop.toString());
		return this;
	}

	public PitstopChecker hasAssignation(Optional<Assignation> optional) {
		assertEquals(optional, pitStop.getAssignation());
		return this;
	}
}
