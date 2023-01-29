package mars.messages;

import java.util.EnumSet;

public record AnomalyIndication(EnumSet<Anomaly> anomalies) {

}
