package mars.mc;

import mars.messages.DroneLandedIndication;
import mars.messages.PlatformCylinderEnterRequest;
import mars.messages.PlatformCylinderLeftIndication;
import mars.messages.PlatformReachabilityIndication;
import mars.messages.PsDemandRequest;

/**
 * All the communication towards the Tower.
 * 
 * All the events related to the communication are called on the
 * {@link McCommsListener} object passed to this McCooms.
 * 
 * Refer to the documentation for each message.
 * 
 * @author mperrando
 *
 * @param <T> the type of the PitStop.
 */
public interface McComms<T> {
	void sendPlatformReachabilityIndication(T ps, final PlatformReachabilityIndication indication);

	void sendPlatformCylinderEnterRequest(final PlatformCylinderEnterRequest req, T ps);

	void sendDroneLandedIndication(DroneLandedIndication indication, T ps);

	void sendPsDemandRequest(final PsDemandRequest psDemandRequest, T ps);

	void sendPlatformCylinderLeftIndication(PlatformCylinderLeftIndication indication, T ps);

	/**
	 * Sets the {@link McCommsListener} that will be notified of all communication
	 * events.
	 * 
	 * @param l the listener.
	 */
	void setListener(McCommsListener<T> l);

	/**
	 * Return if the Tower is active
	 * 
	 * @return true if the Tower is active.
	 */
	boolean isTowerActive();
}
