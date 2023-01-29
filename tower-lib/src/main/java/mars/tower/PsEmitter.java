package mars.tower;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cellply.invosys.signalling.SignalEmitter;

import mars.messages.EventNames;
import mars.messages.PsPlatformReachability;
import mars.messages.PsState;
import mars.messages.PsStateUpdate;
import mars.signals.Signals;

public class PsEmitter implements PsListener {

	private final static Logger LOGGER = LoggerFactory.getLogger(PsEmitter.class);

	private final SignalEmitter signalEmitter;

	public PsEmitter(SignalEmitter signalEmitter) {
		this.signalEmitter = signalEmitter;
	}

	@Override
	public void stateUpdate(PitStop ps, PsState previous, PsState current) {
		emit(Signals.EVENT.name(), EventNames.PIT_STOP_STATE_UPDATE, new PsStateUpdate(ps.id, previous, current));
	}

	private void emit(final String name, final String eventName, final Object payload) {
		try {
			LOGGER.info("Emitting signal: {}[{}]({})", name, eventName, payload);
			signalEmitter.emit(name, eventName, payload);
		} catch (final IOException e) {
			LOGGER.error("Cannot emit signal: {}[{}]({})", name, eventName, payload);
		}
	}

	@Override
	public void logUpdated(PitStop ps, PsLog logLine) {
		emit(Signals.EVENT.name(), EventNames.PIT_STOP_LOG_UPDATE, logLine);
	}

	@Override
	public void reachabilitiesUpdate(PitStop ps, List<PsPlatformReachability> old) {
	}
}
