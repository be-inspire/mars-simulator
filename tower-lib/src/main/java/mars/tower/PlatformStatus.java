package mars.tower;

import java.util.List;

import mars.agent.OnlineStatus;
import mars.messages.GeoCoord;
import mars.messages.PayloadBay;
import mars.messages.PlatformAlarms;

public record PlatformStatus(String pltId, Integer readyPsId, GeoCoord geoCoord, OnlineStatus status,
		PlatformAlarms alarms, long serviceMillis, List<PayloadBay> payloadBays) {

	public PlatformStatus updateStatus(Integer readyPsId, OnlineStatus online, PlatformAlarms alarms,
			List<PayloadBay> payloadBays) {
		return new PlatformStatus(pltId, readyPsId, geoCoord, online, alarms, serviceMillis, payloadBays);
	}
}
