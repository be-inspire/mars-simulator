package mars.platform.sim;

import com.cellply.invosys.ComSystem;

import mars.platform.logics.Bay;
import mars.platform.logics.PlatformInternals;

/**
 * Creates a {@link PlatformInternals} for a simulation.
 * 
 * @author mperrando
 *
 */
@FunctionalInterface
public interface InternalsFactory<B extends Bay<B>> {

	PlatformInternals<B> createInternals(final String confFile, ComSystem comSystem, String name) throws Exception;

}
