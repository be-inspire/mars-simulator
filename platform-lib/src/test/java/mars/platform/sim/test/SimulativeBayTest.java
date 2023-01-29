package mars.platform.sim.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import mars.platform.logics.BayListener;
import mars.platform.logics.Payload;
import mars.platform.sim.SimulativeBay;
import mars.utils.test.ElTestSupport;
import mars.utils.test.TestUtils;

@ExtendWith(MockitoExtension.class)
class SimulativeBayTest {

	private final Payload payload = new Payload("PT1", "PL1");

	static {
		TestUtils.loadLog4jStandard();
	}

	@Mock
	private BayListener<SimulativeBay> listener;

	private final ElTestSupport elSupport = new ElTestSupport();

	private final SimulativeBay out = new SimulativeBay(1, Optional.empty(), .0000005, 45_000);

	@BeforeEach
	void setup() {
		out.addBayListener(listener);
	}

	@Test
	void testRechargeComplete() throws Exception {
		payload.charge = .9999;
		elSupport.runEventAndWait(1_000_000, () -> out.put(payload));
		Mockito.verify(listener).rechargeComplete(out);
		assertEquals(1d, payload.charge, 1e-9);
		assertNull(out.getRestorationInstant());
	}
}
