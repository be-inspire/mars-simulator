package mars.mc;

import java.io.IOException;
import java.util.Optional;

import com.cellply.invosys.ComSystem;
import com.cellply.invosys.agent.Agent;

import eventloop.El;
import mars.heartbeat.LifecycleManager;
import mars.mc.comms.StandardMcMessaging;
import mars.messages.HeartbeatIds;
import mars.simulation.SharedConfiguration;
import mars.simulation.SimUtils;

public final class McUtils<T> {
	private McUtils() {
	}

	/**
	 * Starts a Mission Controller given a ComSystem and the {@link McLogics}.
	 * 
	 * Creates a {@link LifecycleManager} that manages heartbeats, a
	 * {@link McCommsOnComSystem} and then {@link McLogics#start}s the logics when
	 * everything is ready.
	 * 
	 * @param <T>                      the type of the PitStop.
	 * @param comSystem                a ready {@link ComSystem}
	 * @param agentName                the name of this agent on the ComSystem
	 * @param mc                       the logics
	 * @param commsTimeoutMs           the comunication timeout
	 * @param heartbeatPeriodInSeconds the heartbeat period
	 */
	public static <T> void start(ComSystem comSystem, String agentName, McLogics<T> mc, long commsTimeoutMs,
			int heartbeatPeriodInSeconds) {
		try {
			final var lifecycleManager = new LifecycleManager(comSystem, agentName, heartbeatPeriodInSeconds,
					HeartbeatIds.MC, HeartbeatIds.TOWER);
			El.addQuitEvent(() -> lifecycleManager.close());
			final Agent agent = comSystem.createAgent(agentName);
			final var c = new McCommsOnComSystem<T>(r -> new StandardMcMessaging(El.executor(), agent, r),
					lifecycleManager::addAgentLifecycleListener, comSystem.createEmitter(agentName), commsTimeoutMs);
			lifecycleManager.start();
			mc.start(c, c);
			System.out.println("MC " + agentName + " running. Press ^C to exit");
		} catch (final IOException e) {
			e.printStackTrace();
			System.err.println("Cannot create lifecycvle listener, giving up");
			El.quit();
		}
	}

	/**
	 * Parses the command line paraemters and
	 * {@link #start(ComSystem, String, McLogics, long, int)}s the Missionn
	 * Controller.
	 * 
	 * @param <T>  the type of the PitStop.
	 * @param args the command line args
	 * @param mc   the Mission Controller logics
	 * @throws InterruptedException when interrupted
	 * @throws IOException          on I/O errors
	 */
	public static <T> void main(String[] args, final McLogics<T> mc) throws InterruptedException, IOException {

		if (args.length == 0)
			throw new IllegalArgumentException("Agent name missing");
		final String agentName = args[0];
		System.out.println("Starting MC with name " + agentName);
		final String hostName = args.length > 1 ? args[1] : "localhost";
		final var port = Optional.ofNullable(args.length > 2 ? Integer.parseInt(args[2]) : null);

		final SharedConfiguration conf = SimUtils.loadSharedConf();
		System.out.println("With conf: " + conf);
		SimUtils.runSimulator(hostName, port, comSystem -> {
			McUtils.start(comSystem, agentName, mc, conf.commsTimeoutMillis, conf.heartbeatPeriodInSeconds);
		}, conf.timeRate).join();
	}
}
