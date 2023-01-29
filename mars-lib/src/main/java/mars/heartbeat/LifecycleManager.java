package mars.heartbeat;

import java.io.Closeable;
import java.io.IOException;

import com.cellply.invosys.ComSystem;

public class LifecycleManager implements Closeable {

	private static final String SIGNAL_NAME = "HEARTBEAT";
	private static final int MAX_MISSING_HEARTBEATS = 3;
	private final HeartbeatEmitter heartbeatEmitter;
	private final HeartbeatReceiver heartbeatReceiver;

	public LifecycleManager(ComSystem comSystem, String agentName, int heartbeatPeriodInSeconds, String emittedId,
			String interesetedIdFilter) throws IOException {
		heartbeatEmitter = new HeartbeatEmitter(comSystem.createEmitter(agentName), emittedId, SIGNAL_NAME,
				heartbeatPeriodInSeconds);
		heartbeatReceiver = new HeartbeatReceiver(comSystem, interesetedIdFilter, SIGNAL_NAME,
				heartbeatPeriodInSeconds * MAX_MISSING_HEARTBEATS);
	}

	public void start() {
		heartbeatEmitter.start();
	}

	public void addAgentLifecycleListener(AgentLifecycleListener agentLifecycleListener) {
		heartbeatReceiver.addAgentLifeListener(agentLifecycleListener);
	}

	@Override
	public void close() throws IOException {
		heartbeatEmitter.stop();
		heartbeatReceiver.close();
	}
}
