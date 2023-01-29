package mars.platform.sim.test;

import java.time.Instant;
import java.time.temporal.TemporalUnit;

import org.mockito.Mockito;

import eventloop.EventLoop.Event;
import mars.heartbeat.AgentLifecycleListener;
import mars.platform.comms.StandardPlatformMessaging;
import mars.platform.comms.TowerMessageReceiver;
import mars.utils.test.ElTestSupport;

public class TestEnv {

	private final ElTestSupport elSupport = new ElTestSupport();

	public final StandardPlatformMessaging pm = Mockito.mock(StandardPlatformMessaging.class);
	public TowerMessageReceiver tmr;
	public AgentLifecycleListener all;

	public StandardPlatformMessaging messagingMaker(TowerMessageReceiver tmr) {
		this.tmr = tmr;
		return pm;
	}

	public void allConsumer(AgentLifecycleListener all) {
		this.all = all;
	}

	public void runEventAndWait(long millis, Event event) throws Exception {
		elSupport.runEventAndWait(millis, event);
	}

	public Instant at(long amountToAdd, TemporalUnit seconds) {
		return elSupport.at(amountToAdd, seconds);
	}
}
