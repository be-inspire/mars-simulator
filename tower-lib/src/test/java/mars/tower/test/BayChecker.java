package mars.tower.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import mars.messages.PayloadBay;

public class BayChecker {

	private final PayloadBay bay;

	public BayChecker(PayloadBay bay) {
		this.bay = bay;
	}

	public BayChecker isEmpty() {
		assertNull(bay.payload(), "bay not empty: " + bay);
		return this;
	}

	public BayChecker hasPayload(String string) {
		assertEquals(string, bay.payload().payloadType());
		return this;
	}

	public BayChecker isCharged(double level) {
		assertEquals(level, bay.payload().restoredAt());
		return this;
	}
}
