package mars.tower;

import java.util.List;

import mars.messages.PsPlatformReachability;
import mars.messages.PsState;

public class PsListenerAdapter implements PsListener {

	@Override
	public void stateUpdate(PitStop ps, PsState old, PsState state) {
	}

	@Override
	public void logUpdated(PitStop ps, PsLog log) {
	}

	@Override
	public void reachabilitiesUpdate(PitStop ps, List<PsPlatformReachability> old) {
	}

}
