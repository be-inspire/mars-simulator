package mars.mc;

import mars.signals.Signals;

/**
 * All the side communication used for simulation purposes.
 * 
 * @author mperrando
 *
 */
public interface McSideComms {
	/**
	 * Emits a signal to other simulation agents.
	 * 
	 * @param <S>       the type of the signal payload
	 * @param name      the name of the signal {@link Signals}
	 * @param eventName the name of the event
	 * @param payload   the payload of the signal
	 */
	<S> void emitSideSignal(String name, String eventName, S payload);
}
