package mars.heartbeat;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cellply.invosys.signalling.SignalEmitter;

import eventloop.El;
import eventloop.Timeout;

public class HeartbeatEmitter {

	private static final Logger LOGGER = LoggerFactory.getLogger(HeartbeatEmitter.class);

	private final SignalEmitter emitter;
	private final int periodInSeconds;
	private Timeout timeout;

	private final String id;
	private final String category;

	public HeartbeatEmitter(SignalEmitter emitter, String id, String category, int periodInSeconds) {
		this.emitter = emitter;
		this.id = id;
		this.category = category;
		this.periodInSeconds = periodInSeconds;
	}

	public synchronized void start() {
		if (timeout != null)
			return;
		schedule();
	}

	private void schedule() {
		emit();
		timeout = El.setTimeout(periodInSeconds * 1000, this::schedule);
	}

	public synchronized void stop() {
		timeout.cancel();
		this.timeout = null;
	}

	private void emit() {
		LOGGER.debug("Emitting HEARTHBEAT");
		try {
			emitter.emit(category, id, null);
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}
}
