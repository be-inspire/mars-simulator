package mars.mc.samples;
import java.time.Instant;
import java.util.Optional;

import eventloop.Timeout;

public class PitStop {
	public final int requestId;
	public final Drone drone;
	public final Instant requestedAt;
	public Integer pitStopId;
	public String platformId;
	public Instant platformAvailableAt;
	public Instant arrivedAtPlatformCylinder;
	public Instant authorizedEnterCylinderAt;
	public Instant landedAt;
	public Instant leftCylinderAt;
	public Optional<Timeout> travelTimeout = Optional.empty();

	public PitStop(int requestId, Drone drone, Instant requestedAt) {
		this.requestId = requestId;
		this.drone = drone;
		this.requestedAt = requestedAt;
	}

	@Override
	public String toString() {
		return "PitStop [requestId=" + requestId + ", pitStopId=" + pitStopId + ", drone=" + drone + ", platformId="
				+ platformId + ", requestedAt=" + requestedAt + ", platformAvailableAt=" + platformAvailableAt
				+ ", arrivedAtPlatformCylinder=" + arrivedAtPlatformCylinder + ", authorizedEnterCylinderAt="
				+ authorizedEnterCylinderAt + ", landedAt=" + landedAt + ", leftCylinderAt=" + leftCylinderAt + "]";
	}

}
