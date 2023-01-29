package mars.messages;

public record PayloadBay(int bayId, RestoringPayload payload, long prepareMillis) {

}
