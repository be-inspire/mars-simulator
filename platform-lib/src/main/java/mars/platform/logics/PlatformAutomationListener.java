package mars.platform.logics;

/**
 * Receives the events thatoccur on the {@link PlatformAutomationSystem}.
 * 
 * @author mperrando
 *
 */

public interface PlatformAutomationListener {

	/**
	 * A drone has landed with the given id.
	 * 
	 * @param droneId the id of the landed drone.
	 */
	void droneLanded(String droneId);

	/**
	 * A drone has took off with the given id.
	 * 
	 * @param droneId the id of the took off drone.
	 */
	void droneTookOff(String droneId);
}
