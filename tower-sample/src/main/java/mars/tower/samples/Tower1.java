package mars.tower.samples;

import java.util.Optional;

import mars.simulation.SharedConfiguration;
import mars.simulation.SimUtils;
import mars.tower.Tower;
import mars.tower.planner.dummy.DummyPlanner;

/**
 * Parses the command lines and runs a {@link Tower} settings its parameters
 * with a {@link DummyPlanner}.
 * 
 * @author mperrando
 *
 */
public class Tower1 {
	public static void main(String[] args) throws Exception {
		final int psReadyWaitMillis = 30_000;
		final int landingWaitMillis = 15_000;

		final String hostName = args.length > 0 ? args[0] : "localhost";
		final var port = Optional.ofNullable(args.length > 1 ? Integer.parseInt(args[1]) : null);

		final SharedConfiguration conf = SimUtils.loadSharedConf();
		System.out.println("With conf: " + conf);
		SimUtils.runSimulator(hostName, port,
				comSystem -> Tower.run(comSystem, "TOWER", conf.heartbeatPeriodInSeconds,
						new DummyPlanner(psReadyWaitMillis + landingWaitMillis), landingWaitMillis, psReadyWaitMillis,
						conf.commsTimeoutMillis),
				conf.timeRate).join();
	}
}
