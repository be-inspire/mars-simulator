package mars.messages;

import java.util.List;

public record PsDemandResponse(int requestId, int psId, List<PsPlatformAvailability> psPlatformAvailabilities) {

}
