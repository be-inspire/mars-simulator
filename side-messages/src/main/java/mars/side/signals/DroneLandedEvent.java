package mars.side.signals;

public record DroneLandedEvent(String droneId, String pltName, PayloadOnBoard payloadOnBoard) {

}
