package mars.platform.sim.dummy;

import mars.messages.GeoCoord;
import mars.platform.logics.PlatformAutomationSystem;
import mars.platform.logics.PlatformInternals;
import mars.platform.sim.SimulativeBay;

/**
 * A simulated, configurable {@link PlatformInternals} implementation.
 * 
 * @author mperrando
 *
 */
public class DummyPlatformInternals implements PlatformInternals<SimulativeBay> {
	private GeoCoord geoCoord = GeoCoord.NULL;

	private final PlatformAutomationSystem<SimulativeBay> automationSystem;

	public DummyPlatformInternals(PlatformAutomationSystem<SimulativeBay> automationSystem) {
		this.automationSystem = automationSystem;
	}

	@Override
	public GeoCoord getGeoCoord() {
		return geoCoord;
	}

	public void setGeoCoord(GeoCoord geoCoord) {
		this.geoCoord = geoCoord;
	}

	@Override
	public PlatformAutomationSystem<SimulativeBay> getAutomationSystem() {
		return automationSystem;
	}
}
