package mars.tower;

import java.util.Collection;

public interface Lower {
	void planUpdated(Plan plan);

	void addPlatforStatusListener(PlatformStatusListener l);

	Collection<PlatformStatus> getPlatformsStatus();

	Collection<PitStop> getPitStops();

	void addPsListener(PsListener psListener);

	boolean isCylinderBusy(PitStop ps);

	boolean isEngaged(PitStop ps);

}
