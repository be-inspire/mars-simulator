package mars.mc;

/**
 * The Logics of a Mission Controller.
 * 
 * Implement this class to create different Mission Controllers.
 * 
 * @author mperrando
 *
 * @param <T> the type that represents a PitStop.
 */
public interface McLogics<T> {
	/**
	 * Everything is ready for this {@link McLogics} to start its work.
	 * 
	 * @param comms     the communication channel towards the Tower
	 * @param sideComms the side channel (used only in simulation)
	 */
	void start(McComms<T> comms, McSideComms sideComms);
}
