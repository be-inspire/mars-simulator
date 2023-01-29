package mars.tower.test;

import mars.tower.PitStop;
import mars.tower.PlatformStatus;
import mars.tower.WorldModel;

public class TestWorldModel extends WorldModel {
	void setPlatformStatus(PlatformStatus... platformsStatus) {
		this.platformsStatus.clear();
		for (final PlatformStatus s : platformsStatus) {
			this.platformsStatus.put(s.pltId(), s);
		}
	}

	public void setPitStops(PitStop... pitStops) {
		this.pitStops.clear();
		for (final var p : pitStops) {
			this.pitStops.put(p.id, p);
		}
	}
}
