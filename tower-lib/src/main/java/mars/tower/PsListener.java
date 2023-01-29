package mars.tower;

import java.util.List;

import mars.messages.PsPlatformReachability;
import mars.messages.PsState;

public interface PsListener {

	PsListener NULL = new PsListenerAdapter();

	void stateUpdate(PitStop ps, PsState old, PsState state);

	void logUpdated(PitStop ps, PsLog log);

	void reachabilitiesUpdate(PitStop ps, List<PsPlatformReachability> old);

}
