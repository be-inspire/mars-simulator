package mars.messages;

import java.util.Collections;
import java.util.List;

public record PlatformStatus(Integer readyPsId, PlatformAlarms alarms, List<PayloadBay> payloadBays, long serviceMillis) {

	public PlatformStatus normalize() {
		if (payloadBays == null)
			return new PlatformStatus(readyPsId, alarms, Collections.emptyList(), serviceMillis);
		else
			return this;
	}
}
