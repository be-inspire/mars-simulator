package mars.messages;

import java.util.List;

/**
 * Sent in response to a {@link PlatformAvailabilityIndication}.
 *
 * The MC can sent the {@link PsPlatformReachability} list.
 *
 * @param psId                     the id of the PS
 * @param psPlatformReachabilities the list of {@link PsPlatformReachability}
 *                                 may be sent
 *
 * @author mperrando
 *
 * @see PlatformAvailabilityIndication
 */
public record PlatformAvailabilityConfirm(int psId, List<PsPlatformReachability> psPlatformReachabilities) {

}
