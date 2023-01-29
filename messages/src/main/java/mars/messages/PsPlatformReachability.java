package mars.messages;

import java.time.Instant;

/**
 * The reachability data of a drone to a specific PLT.
 *
 * @param platformId the id of the platform this data referes to
 * @param etaMin     the lower bound {@link Instant} of the ETA
 * @param etaMax     the upper bound {@link Instant} of the ETA
 *
 * @author mperrando
 *
 * @see PlatformReachabilityIndication
 * @see PlatformAvailabilityConfirm
 */
public record PsPlatformReachability(String platformId, Instant etaMin, Instant etaMax) {

}
