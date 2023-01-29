package mars.messages;

import java.time.Instant;

/**
 * Sent by an MC to TOWER to request a new PS.
 *
 * @param requestId      a unique id for the request for the sending MC
 * @param payloadType    the type of the payload the drone must be served
 * @param endOfDroneLife the {@link Instant} at which the drone will end its
 *                       life, if not served
 *
 * @author mperrando
 *
 */
public record PsDemandRequest(int requestId, String payloadType, Instant endOfDroneLife) {

}
