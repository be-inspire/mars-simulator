package mars.heartbeat;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cellply.invosys.ComSystem;
import com.cellply.invosys.signalling.SignalDto;
import com.cellply.invosys.signalling.SignalSubscriber;

import eventloop.El;
import eventloop.Timeout;

public class HeartbeatReceiver implements Closeable {

	private static final int DIE_PERIODS = 2;

	private static final Logger LOGGER = LoggerFactory.getLogger(HeartbeatReceiver.class);

	private final SignalSubscriber signalSubscriber;
	private final Map<String, Timeout> agents = new HashMap<>();
	private final List<AgentLifecycleListener> listeners = new LinkedList<>();
	private final int lostTimeoutInSeconds;

	public HeartbeatReceiver(ComSystem comSystem, String idFilter, String category, int expectedPeriodInSeconds)
			throws IOException {
		final var executor = El.executor();
		signalSubscriber = comSystem.createSignalSubscriber(category, idFilter,
				s -> executor.accept(() -> this.consume(s)));
		this.lostTimeoutInSeconds = expectedPeriodInSeconds * DIE_PERIODS;
	}

	@Override
	public void close() throws IOException {
		signalSubscriber.close();
	}

	private void notifyLost(String agentName, String category) {
		listeners.stream().forEach(l -> l.agentLost(agentName, category));
	}

	private void consume(SignalDto dto) {
		LOGGER.debug("HEARTBEAT received " + dto.agentId + " " + dto.id);
		final String agentName = dto.agentId;
		final var timeout = agents.get(agentName);
		agents.put(agentName, El.setTimeout(lostTimeoutInSeconds * 1000L, () -> {
			agents.put(agentName, Timeout.NULL);
			notifyLost(agentName, dto.id);
		}));
		if (timeout == null) {
			emitAgentDiscovered(agentName, dto.id);
		} else {
			if (timeout == Timeout.NULL) {
				emitAgentReturned(agentName, dto.id);
			} else {
				timeout.cancel();
			}
		}
	}

	public void addAgentLifeListener(AgentLifecycleListener l) {
		this.listeners.add(l);
	}

	private void emitAgentDiscovered(String agentName, String category) {
		listeners.stream().forEach(l -> l.agentDiscovered(agentName, category));
	}

	private void emitAgentReturned(String agentName, String category) {
		listeners.stream().forEach(l -> l.agentReturned(agentName, category));

	}
}
