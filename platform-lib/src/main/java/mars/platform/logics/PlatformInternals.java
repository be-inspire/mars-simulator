package mars.platform.logics;

import mars.messages.GeoCoord;

public interface PlatformInternals<B extends Bay<B>> {

	static <B extends Bay<B>> PlatformInternals<B> NULL() {
		return new PlatformInternals<B>() {

			@Override
			public PlatformAutomationSystem<B> getAutomationSystem() {
				return PlatformAutomationSystem.NULL();
			}

			@Override
			public GeoCoord getGeoCoord() {
				return GeoCoord.NULL;
			}
		};
	}

	PlatformAutomationSystem<B> getAutomationSystem();

	GeoCoord getGeoCoord();

}
