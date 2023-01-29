package mars.tower;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import mars.agent.OnlineStatus;

public class WorldModel {

	private final List<PlatformStatusListener> platformStatusListeners = new LinkedList<>();
	protected final Map<String, PlatformStatus> platformsStatus = new HashMap<>();
	protected final Map<Integer, PitStop> pitStops = new HashMap<>();
	private final Collection<PitStop> buriedPitstops = new LinkedList<>();

	public void addPlatforStatusListener(PlatformStatusListener l) {
		platformStatusListeners.add(l);
	}

	public Collection<PlatformStatus> getPlatformsStatus() {
		return platformsStatus.values();
	}

	private void notifyListeners(Consumer<PlatformStatusListener> consumer) {
		for (final PlatformStatusListener l : platformStatusListeners) {
			consumer.accept(l);
		}
	}

	public void updatePlatform(String pltId, PlatformStatus s) {
		final var old = Optional.ofNullable(platformsStatus.get(pltId))
				.orElse(new PlatformStatus(pltId, null, null, OnlineStatus.OFFLINE, null, 0, null));
		platformsStatus.put(pltId, s);
		notifyListeners(l -> l.platformStatusUpdate(old, s));
	}

	public PlatformStatus getPlatformStatus(String pltId) {
		final PlatformStatus result = platformsStatus.get(pltId);
		if (result == null)
			throw new RuntimeException("No platform with name " + pltId);
		return result;
	}

	public void insertPitStop(PitStop pitStop) {
		pitStops.put(pitStop.id, pitStop);
		notifyPsUpdated(pitStop);
	}

	public Optional<PitStop> getPitStop(int pitStopId) {
		return Optional.ofNullable(pitStops.get(pitStopId));
	}

	public void notifyPsUpdated(PitStop pitStop) {
		notifyListeners(l -> l.psStatusUpdate(pitStop));
	}

	public Collection<PitStop> getPitStops() {
		return pitStops.values();
	}

	public Collection<PitStop> getBuriedPitStops() {
		return buriedPitstops;
	}

	public void buryPitStop(PitStop ps) {
		pitStops.remove(ps.id);
		buriedPitstops.add(ps);
		ps.buried();
	}
}
