package mars.platform.samples;

import java.io.File;
import java.io.IOException;

import com.cellply.invosys.ComSystem;

import eventloop.El;
import mars.platform.logics.PlatformInternals;
import mars.platform.sim.PltUtils;
import mars.platform.sim.dummy.ConfiguratorFromFile;
import mars.platform.sim.dummy.DummyAutomationSystem;
import mars.platform.sim.dummy.DummyPlatformInternals;

/**
 * Runs a Platform Simulator creating a {@link DummyPlatformInternals} with a
 * {@link DummyAutomationSystem}.
 * 
 * @author mperrando
 *
 */
public class Platform1 {
	public static void main(String[] args) throws Exception {
		PltUtils.main(args, (confFile, comSystem, name) -> createInternals(confFile, comSystem, name));
	}

	private static PlatformInternals<?> createInternals(final String confFile, ComSystem comSystem, String name)
			throws IOException {
		final DummyAutomationSystem automationSystem = new DummyAutomationSystem(comSystem, name, El.executor());
		final DummyPlatformInternals internals = new DummyPlatformInternals(automationSystem);
		new ConfiguratorFromFile().configure(internals, automationSystem, new File(confFile));
		return internals;
	}
}
