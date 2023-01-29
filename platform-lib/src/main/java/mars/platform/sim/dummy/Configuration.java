package mars.platform.sim.dummy;

import java.util.List;

import mars.messages.GeoCoord;

class Configuration {
	public static class BayConf {
		public String payloadType;
		public Double chargePerSecond;
		public Integer number;
		public Long prepareMillis;
	}

	public GeoCoord geoCoord;
	public List<BayConf> bays;
	public Integer serviceMillis;
}
