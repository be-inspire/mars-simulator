package mars.tower;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cellply.invosys.ComSystem;
import com.cellply.invosys.signalling.SignalEmitter;

import eventloop.El;
import mars.heartbeat.AgentLifecycleListener;
import mars.heartbeat.LifecycleManager;
import mars.messages.HeartbeatIds;
import mars.tower.comms.McMessageReceiver;
import mars.tower.comms.PlatformMessageReceiver;
import mars.tower.comms.StandardTowerMessaging;

public class Tower {

	private final static Logger LOGGER = LoggerFactory.getLogger(Tower.class);

	public static void run(ComSystem comSystem, String agentName, int heartbeatPeriodInSeconds, Planner planner,
			long landingWaitMillis, long psReadyWaitMillis, long commsTimeoutMillis) {
		try {
			final var lifecycleManager = new LifecycleManager(comSystem, agentName, heartbeatPeriodInSeconds,
					HeartbeatIds.TOWER, "#");
			El.addQuitEvent(() -> lifecycleManager.close());
			final var agent = comSystem.createAgent(agentName);
			new Tower((pmr, mmr) -> new StandardTowerMessaging(El.executor(), agent, pmr, mmr),
					comSystem.createEmitter(agentName), lifecycleManager::addAgentLifecycleListener, commsTimeoutMillis,
					landingWaitMillis, psReadyWaitMillis, planner);
			lifecycleManager.start();
			System.out.println("TOWER running. Press ^C to exit");
		} catch (final IOException e) {
			e.printStackTrace();
			System.err.println("Cannot create lifecycle listener, giving up");
			El.quit();
		}
	}

	/**
	 * Creates a {@link Tower} composed by {@link StandardLower},
	 * {@link StandardUpper} and the given {@link Planner}.
	 * 
	 * This {@link Tower} emits PS events and errors on the {@link SignalEmitter},
	 * the first using a {@link PsEmitter} and saves each PS log on the file system
	 * using a {@link PsFileDumper}.
	 * 
	 * @param messagingMaker     creates a {@link TowerMessaging}
	 * @param signalEmitter      emits signals
	 * @param allConsumer        receives the {@link AgentLifecycleListener}
	 * @param commsTimeoutMillis timeout in ms for the communications
	 * @param psReadyWaitMillis  time to wait for a PS to be ready on the Platform
	 * @param landingWaitMillis  time to wait a drone to land on the Platform
	 * @param planner            the {@link Planner} that calculates the
	 *                           {@link Plan}
	 */
	public Tower(BiFunction<PlatformMessageReceiver, McMessageReceiver, TowerMessaging> messagingMaker,
			SignalEmitter signalEmitter, Consumer<AgentLifecycleListener> allConsumer, long commsTimeoutMillis,
			long psReadyWaitMillis, long landingWaitMillis, Planner planner) {
		LOGGER.info("Creating lower level");
		final Lower lower = new StandardLower(new WorldModel(), messagingMaker, allConsumer,
				new ErrorSignaler(signalEmitter), commsTimeoutMillis, psReadyWaitMillis, landingWaitMillis);
		lower.addPsListener(new PsEmitter(signalEmitter));
		try {
			lower.addPsListener(new PsFileDumper(Paths.get("pit_stops")));
		} catch (final IOException e) {
			LOGGER.error("Cannot create PsFileDumper. PS will not be dumped", e);
		}
		LOGGER.info("Creating upper level");
		new StandardUpper(lower, planner);
	}
}
