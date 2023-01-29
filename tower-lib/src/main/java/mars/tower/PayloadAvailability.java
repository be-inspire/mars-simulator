package mars.tower;

import java.time.Instant;

import mars.messages.PayloadBay;

public record PayloadAvailability(String pltId, PayloadBay bay, Instant at) {

}
