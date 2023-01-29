package mars.tower;

import java.io.IOException;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cellply.invosys.signalling.SignalEmitter;

import mars.signals.Signals;

public class ErrorSignaler implements Consumer<Throwable> {
	private final static Logger LOGGER = LoggerFactory.getLogger(ErrorSignaler.class);

	private final SignalEmitter signalEmitter;

	public ErrorSignaler(SignalEmitter signalEmitter) {
		this.signalEmitter = signalEmitter;
	}

	@Override
	public void accept(Throwable t) {
		try {
			signalEmitter.emit(Signals.ERROR.name(), "mars.tower.exception", t);
		} catch (final IOException e) {
			LOGGER.error("Cannot emit side signal: {}[{}]({})", Signals.ERROR.name(), "mars.tower.exception", t);
		}

	}

}
