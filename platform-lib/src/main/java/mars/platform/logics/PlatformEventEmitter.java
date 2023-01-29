package mars.platform.logics;

/**
 * Emits the Platform events.
 *
 */

public interface PlatformEventEmitter {
	/**
	 * Call to emit a falnge unavailable event.
	 * 
	 * @param unavailable true if the flange is unavailable
	 */
	void emitFlangeUnavailable(boolean unavailable);
}
