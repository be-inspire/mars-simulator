package mars.messages;

import java.util.List;

/**
 * Sent from TOWER to MC to indicate the set of available PLTs for a PS for the
 * first time or an update.
 *
 * @param psId                     the id of th PS
 * @param psPlatformAvailabilities the list of {@link PsPlatformAvailability}
 *                                 that depicts the available PLTs for the
 *                                 indicated PS
 *
 * @author mperrando
 *
 * @see PlatformAvailabilityConfirm
 *
 */
public record PlatformAvailabilityIndication(int psId, List<PsPlatformAvailability> psPlatformAvailabilities) {
}
