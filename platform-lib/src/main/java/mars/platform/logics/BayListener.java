package mars.platform.logics;

/**
 * Listens to bay events.
 * 
 * @author mperrando
 *
 */
public interface BayListener<B extends Bay<B>> {

	/**
	 * The payload in the given bay is ready to be used.
	 * 
	 * @param bay the bay
	 */
	void rechargeComplete(B bay);

	/**
	 * The content of the given bay has been updated.
	 * 
	 * Called when a payload is taken from or put into a bay.
	 * 
	 * @param bay the bay
	 */
	void bayContentUpdate(B bay);
}
