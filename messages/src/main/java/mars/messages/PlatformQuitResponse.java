package mars.messages;

import java.time.Instant;

public record PlatformQuitResponse(int lastPsId, Instant lastServiceAt) {

}
