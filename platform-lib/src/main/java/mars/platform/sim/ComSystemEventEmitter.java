package mars.platform.sim;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cellply.invosys.ComSystem;
import com.cellply.invosys.signalling.SignalEmitter;

import mars.platform.logics.PlatformEventEmitter;
import mars.signals.Events;
import mars.signals.Signals;

/**
 * A {@link PlatformEventEmitter} implmentations that sends events through a
 * {@link ComSystem}.
 * 
 * @author mperrando
 *
 */
public class ComSystemEventEmitter implements PlatformEventEmitter {

	private final static Logger LOGGER = LoggerFactory.getLogger(ComSystemEventEmitter.class);
	private final SignalEmitter signalEmitter;

	public ComSystemEventEmitter(SignalEmitter signalEmitter) {
		this.signalEmitter = signalEmitter;
	}

	@Override
	public void emitFlangeUnavailable(boolean unavailable) {
		emit(Signals.EVENT.name(), Events.FLANGE_UNAVAILABLE.name(), unavailable);
	}

	private <T> void emit(String name, String eventName, T payload) {
		try {
			signalEmitter.emit(name, eventName, payload);
		} catch (final IOException e) {
			LOGGER.error("Cannot emit signal: {}[{}]({})", name, eventName, payload);
		}
	}

}
