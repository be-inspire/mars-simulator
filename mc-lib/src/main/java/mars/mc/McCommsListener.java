package mars.mc;

import mars.messages.DroneLandedConfirm;
import mars.messages.PlatformAssignmentConfirm;
import mars.messages.PlatformAssignmentIndication;
import mars.messages.PlatformAvailabilityConfirm;
import mars.messages.PlatformAvailabilityIndication;
import mars.messages.PlatformCylinderEnterResponse;
import mars.messages.PlatformCylinderLeftConfirm;
import mars.messages.PsAbortConfirm;
import mars.messages.PsAbortIndication;
import mars.messages.PsCompletedIndication;
import mars.messages.PsDemandResponse;

/**
 * Receives all the notifications from the communication system.
 * 
 * Two kind of events are present:
 * <ul>
 * <li>incoming messages form the Tower * events</li>
 * <li>related to outgoing messages to the Tower</li>
 * </ul>
 * 
 * Every message sent through the {@link McComms} has three callbacks: the first
 * is called if the response is ok, the second if the response is an error, and
 * the third if the response did not arrive in time.
 * 
 * @author mperrando
 *
 * @param <T> the type of the PitStop.
 */
public interface McCommsListener<T> {

	/**
	 * A {@link PsAbortIndication} has been received.
	 * 
	 * @param p the received indication
	 * @return the {@link PsAbortConfirm} to return to the caller
	 */
	PsAbortConfirm onPsAbortIndication(PsAbortIndication p);

	/**
	 * A {@link PlatformAssignmentIndication} has been received.
	 * 
	 * @param p the received indication
	 * @return the {@link PlatformAssignmentConfirm} to return to the caller
	 */
	PlatformAssignmentConfirm onPlatformAssignmentIndication(PlatformAssignmentIndication p);

	/**
	 * A {@link PlatformAvailabilityIndication} has been received.
	 * 
	 * @param p the received indication
	 * @return the {@link PlatformAvailabilityConfirm} to return to the caller
	 */
	PlatformAvailabilityConfirm onPlatformAvailabilityIndication(PlatformAvailabilityIndication p);

	/**
	 * A {@link PsCompletedIndication} has been received.
	 * 
	 * @param p the received indication
	 * @return the {@link PsCompletedIndication} to return to the caller
	 */
	PsCompletedIndication onPsCompletedIndication(PsCompletedIndication p);

	/**
	 * When a
	 * {@link McComms#sendPlatformReachabilityIndication(Object, mars.messages.PlatformReachabilityIndication)}
	 * is confirmed.
	 * 
	 * @param ps the ps passed to the send method.
	 */
	void onPlatformReachabilityConfirm(T ps);

	/**
	 * When a
	 * {@link McComms#sendPlatformReachabilityIndication(Object, mars.messages.PlatformReachabilityIndication)}
	 * has timed out.
	 * 
	 * @param ps the ps passed to the send method.
	 */
	void onPlatformReachabilityTimeout(T ps);

	/**
	 * When a
	 * {@link McComms#sendPlatformReachabilityIndication(Object, mars.messages.PlatformReachabilityIndication)}
	 * receives an error.
	 * 
	 * @param t  the error raised on the other side of the communication
	 * @param ps the ps passed to the send method.
	 */
	void onPlatformReachabilityError(Throwable t, T ps);

	/**
	 * When a
	 * {@link McComms#sendPlatformCylinderEnterRequest(mars.messages.PlatformCylinderEnterRequest, Object)}
	 * has timed out.
	 * 
	 * @param r  the response
	 * @param ps the ps passed to the send method.
	 */

	void onCylinderEnterResponse(PlatformCylinderEnterResponse r, T ps);

	/**
	 * When a
	 * {@link McComms#sendPlatformCylinderEnterRequest(mars.messages.PlatformCylinderEnterRequest, Object)}
	 * receives the response.
	 * 
	 * @param ps the ps passed to the send method.
	 */
	void onCylinderEnterTimeout(T ps);

	/**
	 * When a
	 * {@link McComms#sendPlatformCylinderEnterRequest(mars.messages.PlatformCylinderEnterRequest, Object)}
	 * receives an error.
	 * 
	 * @param t  the error raised on the other side of the communication
	 * @param ps the ps passed to the send method.
	 */
	void onCylinderEnterError(Throwable t, T ps);

	/**
	 * When a
	 * {@link McComms#sendDroneLandedIndication(mars.messages.DroneLandedIndication, Object)}
	 * is confirmed.
	 * 
	 * @param r  the confirm
	 * @param ps the ps passed to the send method.
	 */
	void onDroneLandedConfirmed(DroneLandedConfirm r, T ps);

	/**
	 * When a
	 * {@link McComms#sendDroneLandedIndication(mars.messages.DroneLandedIndication, Object)}
	 * has timed out.
	 * 
	 * @param ps the ps passed to the send method.
	 */
	void onDroneLandedTimeout(T ps);

	/**
	 * When a
	 * {@link McComms#sendDroneLandedIndication(mars.messages.DroneLandedIndication, Object)}
	 * receives an error.
	 * 
	 * @param t  the error raised on the other side of the communication
	 * @param ps the ps passed to the send method.
	 */
	void onDroneLandedError(Throwable t, T ps);

	/**
	 * When a
	 * {@link McComms#sendPlatformCylinderEnterRequest(mars.messages.PlatformCylinderEnterRequest, Object)}
	 * receives the response.
	 * 
	 * @param psdemandresponse the response
	 * @param ps               the ps passed to the send method.
	 */
	void onPsDemandResponse(PsDemandResponse psdemandresponse, T ps);

	/**
	 * When a
	 * {@link McComms#sendPlatformCylinderEnterRequest(mars.messages.PlatformCylinderEnterRequest, Object)}
	 * has timed out.
	 * 
	 * @param ps the ps passed to the send method.
	 */
	void onPsDemandTimeout(T ps);

	/**
	 * When a
	 * {@link McComms#sendPlatformCylinderEnterRequest(mars.messages.PlatformCylinderEnterRequest, Object)}
	 * receives an error.
	 * 
	 * @param t  the error raised on the other side of the communication
	 * @param ps the ps passed to the send method.
	 */
	void onPsDemandError(Throwable t, T ps);

	/**
	 * When a
	 * {@link McComms#sendPlatformCylinderLeftIndication(mars.messages.PlatformCylinderLeftIndication, Object)}
	 * is confirmed.
	 * 
	 * @param c  the confirm
	 * @param ps the ps passed to the send method.
	 */
	void onCylinderLeftConfirmed(PlatformCylinderLeftConfirm c, T ps);

	/**
	 * When a
	 * {@link McComms#sendPlatformCylinderLeftIndication(mars.messages.PlatformCylinderLeftIndication, Object)}
	 * has timed out.
	 * 
	 * @param ps the ps passed to the send method.
	 */
	void onCylinderLeftTimeout(T ps);

	/**
	 * When a
	 * {@link McComms#sendPlatformCylinderLeftIndication(mars.messages.PlatformCylinderLeftIndication, Object)}
	 * receives an error.
	 * 
	 * @param t  the error raised on the other side of the communication
	 * @param ps the ps passed to the send method.
	 */
	void onCylinderLeftError(Throwable t, T ps);

	/**
	 * Tower has been discovered for the first time.
	 * 
	 * @param agentName the agent name of the Tower.
	 */
	void onTowerDiscovered(String agentName);

	/**
	 * Tower is lost.
	 * 
	 * @param agentName the agent name of the Tower.
	 */
	void onTowerLost(String agentName);

	/**
	 * Tower is again availble after it has been lost.
	 * 
	 * @param agentName the agent name of the Tower.
	 */
	void onTowerReturned(String agentName);

}
