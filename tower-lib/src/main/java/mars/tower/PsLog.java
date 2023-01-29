package mars.tower;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import mars.messages.PsPlatformReachability;
import mars.messages.PsState;

public record PsLog(Instant instant, Object message) {
	public record Assigned(Optional<Assignation> assignation) {
	}

	public record StateUpdate(PsState from, PsState to) {
	}

	public record ReachabilitiesUpdate(List<PsPlatformReachability> psPlatformReachabilities) {
	}

	public record Buried() {
	}

	public record DroneLandedIndicationTimedOut() {
	}
}
