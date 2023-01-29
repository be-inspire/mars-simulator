package mars.messages;

import java.util.List;

/**
 * Message sent from a MC to TOWER to indicate the reachability condition for
 * each of the available PLTs.
 *
 * It can be sent unsolicited if the MC detects a shift in the ETA to the
 * current assigned PLT of the drone for the indicated PS, or it must follow a
 * {@link PlatformAvailabilityIndication} if the reachabilities have not been
 * already sent in the {@link PlatformAvailabilityConfirm}
 *
 *
 * @param psId                     the id of the PS
 * @param psPlatformReachabilities a list of {@link PsPlatformReachability} that
 *                                 indicates the estimated travel details for
 *                                 each PLT
 *
 * @author mperrando
 *
 * @see PlatformReachabilityConfirm
 *
 */
public record PlatformReachabilityIndication(int psId, List<PsPlatformReachability> psPlatformReachabilities) {

}
