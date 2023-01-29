package mars.tower.test;

import mars.tower.PitStop;

public class UpdaterTestEnv extends BaseTestEnv {

	final TestWorldModel worldModel = new TestWorldModel();

	public void setPitStops(PitStop... pitStops) {
		worldModel.setPitStops(pitStops);
	}

//	public PitStop ps(int id, String mcId, Duration requestedAfter, long serviceMillis, PsDemandRequest request,
//			Consumer<Builder> c) {
//		return ps(id, mcId, requestedAfter, request, p -> p.serviceMillis(serviceMillis));
//	}
}
