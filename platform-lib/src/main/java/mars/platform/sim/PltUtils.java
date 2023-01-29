package mars.platform.sim;

import java.io.IOException;
import java.util.Optional;

import com.cellply.invosys.ComSystem;

import eventloop.El;
import mars.heartbeat.LifecycleManager;
import mars.messages.HeartbeatIds;
import mars.platform.comms.StandardPlatformMessaging;
import mars.platform.logics.Bay;
import mars.platform.logics.PlatformInternals;
import mars.platform.logics.PlatformLogics;
import mars.simulation.SharedConfiguration;
import mars.simulation.SimUtils;
import mars.utils.FailingFactory;

public class PltUtils {

	/**
	 * Parses the command line parameters and starts a Platform Simulator.
	 * 
	 * @param <B>              the bay type
	 * @param args             the command line args
	 * @param internalsFactory the factory thatcreatesthe {@link PlatformInternals}
	 * @throws InterruptedException when interrupted
	 * @throws IOException          on I/O errors
	 */
	public static <B extends Bay<B>> void main(String[] args, final InternalsFactory<B> internalsFactory)
			throws InterruptedException, IOException {

		if (args.length == 0)
			throw new IllegalArgumentException("Missing agent name");
		final String name = args[0];
		if (args.length == 1)
			throw new IllegalArgumentException("Missing configuration file name");
		final String confFile = args[1];
		System.out.println("Starting PLT with name " + name + " conf file " + confFile);
		final String hostName = args.length > 2 ? args[2] : "localhost";
		final var port = Optional.ofNullable(args.length > 3 ? Integer.parseInt(args[3]) : null);

		final var serviceTimeMarginMillis = 10000;

		final SharedConfiguration conf = SimUtils.loadSharedConf();
		System.out.println("With conf: " + conf);
		SimUtils.runSimulator(hostName, port, comSystem -> {
			run(comSystem, name, conf.heartbeatPeriodInSeconds, serviceTimeMarginMillis,
					() -> internalsFactory.createInternals(confFile, comSystem, name), conf.commsTimeoutMillis);
		}, conf.timeRate).join();
	}

	/**
	 * Runs a Platform, using {@link PlatformLogics} creating
	 * {@link PlatformInternals} with the given factory.
	 * 
	 * @param <B>                      the bay type
	 * @param comSystem                a ready {@link ComSystem}
	 * @param agentName                the name of thss agent on ComSystem
	 * @param heartbeatPeriodInSeconds the heartbeat period
	 * @param serviceTimeMarginMillis  the extra margin for waiting service
	 *                                 completion
	 * @param internalsFactory         the facotry of the {@link PlatformInternals}
	 * @param commsTimeoutMillis       comms timeout in millis
	 */
	public static <B extends Bay<B>> void run(ComSystem comSystem, String agentName, final int heartbeatPeriodInSeconds,
			long serviceTimeMarginMillis, final FailingFactory<PlatformInternals<B>> internalsFactory,
			long commsTimeoutMillis) {
		try {
			final var agent = comSystem.createAgent(agentName);
			final var internals = internalsFactory.create();
			try {
				final var lifecycleManager = new LifecycleManager(comSystem, agentName, heartbeatPeriodInSeconds,
						HeartbeatIds.PLT, HeartbeatIds.TOWER);
				El.addQuitEvent(() -> lifecycleManager.close());
				new PlatformLogics<B>((tmr) -> new StandardPlatformMessaging(El.executor(), agent, tmr),
						lifecycleManager::addAgentLifecycleListener,
						new ComSystemEventEmitter(comSystem.createEmitter(agentName)), commsTimeoutMillis,
						serviceTimeMarginMillis, agent.getName()).setModel(internals);
				lifecycleManager.start();
				System.out.println("Platform running. Press ^C to exit");
			} catch (final Exception e) {
				System.err.println("Cannot create lifecycle listener, giving up");
				e.printStackTrace();
				El.quit();
			}
		} catch (final Exception e) {
			System.err.println("Cannot create platform internals, giving up");
			e.printStackTrace();
			El.quit();
		}
	}
}
