package mars.tower;

import java.time.Instant;

public record Assignation(BayAssignation bay, Instant at, Instant occupiedFrom, Instant occupiedTo) {

}
