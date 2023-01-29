package mars.messages;

import java.time.Instant;

public record RestoringPayload(String id, String payloadType, double restoredAt, Instant willRestoreAt) {

}
