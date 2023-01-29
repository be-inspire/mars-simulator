package mars.messages;

import java.time.Instant;

public record PlatformAssignmentIndication(int psId, String platformId, Instant at) {

}
