package mars.platform.logics;

import java.util.concurrent.CompletionStage;

/**
 * The operation that can be invoked on a Platform.
 * 
 * @author mperrando
 *
 */
public interface Platform {
	/**
	 * This Platform must quit its operation.
	 * 
	 * Call this method when you cleanly want to quit this Platform. The Tower will
	 * not schedule new PS towards this Platform, and, when all already scehduled
	 * PSs are dpne, thequit canbe done.
	 * 
	 * @return a {@link CompletionStage} that completes when this Platform can quit
	 *         beacuse all services assigne to itare done.
	 */
	CompletionStage<Void> quit();

	/**
	 * Resets the flange unavailable alarm.
	 * 
	 * Call this methodwhen the falnge unavailablealarm has been set and an operatro
	 * has checked that the flange is again available.
	 */
	void resetFlangeUnavailableAlarm();
}
