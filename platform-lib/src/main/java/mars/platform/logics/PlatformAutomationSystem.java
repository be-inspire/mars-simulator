package mars.platform.logics;

import java.util.List;
import java.util.concurrent.CompletionStage;

import mars.messages.CannotReadyPayload;
import mars.messages.CannotStartPsException;

/**
 * The automation system of a Platform.
 * 
 * @author mperrando
 *
 */
public interface PlatformAutomationSystem<B extends Bay<B>> {

	static <B extends Bay<B>> PlatformAutomationSystem<B> NULL() {
		return new PlatformAutomationSystem<B>() {

			@Override
			public CompletionStage<Void> startPitStop(Runnable onPsUnprepared) {
				throw new RuntimeException("Not attachhed");
			}

			@Override
			public void preparePayload(int bayId, Runnable onDone) {
				throw new RuntimeException("Not attachhed");
			}

			@Override
			public String getLandedDroneId() {
				throw new RuntimeException("Not attachhed");
			}

			@Override
			public void abortPitStop(Runnable r) {
				throw new RuntimeException("Not attachhed");
			}

			@Override
			public void addPlatformAutomationListener(PlatformAutomationListener l) {
				throw new RuntimeException("Not attachhed");
			}

			@Override
			public List<B> getPayloadBays() {
				throw new RuntimeException("Not attachhed");
			}

			@Override
			public long getServiceTime() {
				throw new RuntimeException("Not attachhed");
			}
		};
	}

	/**
	 * Adds a listener to this {@link PlatformAutomationSystem}.
	 * 
	 * @param l the listener to add
	 */
	void addPlatformAutomationListener(PlatformAutomationListener l);

	/**
	 * Starts the PitStop.
	 * 
	 * @param onPsUnprepared called when the Pitstop is over and it has been
	 *                       unprepared
	 * @return a {@link CompletionStage} that completes when the PitStop has
	 *         finished
	 * @throws CannotStartPsException thrown if the PitStop cannot start
	 */
	CompletionStage<Void> startPitStop(Runnable onPsUnprepared) throws CannotStartPsException;

	/**
	 * Return the current landed drone id.
	 * 
	 * @return the current landed drone id
	 */
	String getLandedDroneId();

	/**
	 * Instruct this {@link PlatformAutomationSystem} to prepare the payload in the
	 * indicated bay.
	 * 
	 * @param bayId  the bay form whjich take the payload.
	 * @param onDone called when the payload is ready for the PitStop
	 * @throws CannotReadyPayload thrown if the payload cannot be prepared
	 */
	void preparePayload(int bayId, Runnable onDone) throws CannotReadyPayload;

	/**
	 * Aborts the current PitStop and returns the payload to the last bay from which
	 * a payload has been taken.
	 * 
	 * @param r called when the payload is in the bay
	 */
	void abortPitStop(Runnable r);

	/**
	 * Returns the bays with their status.
	 * 
	 * @return the bays with their status
	 */
	List<B> getPayloadBays();

	/**
	 * Returns the service time of the PitStop.
	 * 
	 * @return the service time of the PitStop.
	 */
	long getServiceTime();
}
