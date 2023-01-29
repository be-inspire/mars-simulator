package mars.simulation;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

import com.cellply.invosys.ComSystem;
import com.cellply.invosys.converters.JacksonJsonConverter;
import com.cellply.invosys.rabbitmq.RabbitComSystem;
import com.google.gson.Gson;

import eventloop.El;
import eventloop.EventLoop;
import eventloop.StandardEventLoop;
import mars.time.FakedEventLoop;
import mars.time.TimeFake;

public class SimUtils {

	/**
	 * Starts a simulator connecting it through a ComSystem broker to other agents
	 * inside an {@link EventLoop}.
	 * 
	 * An optional time rate acceleration cna be passed.
	 * 
	 * @param hostName          the host name of the ConSystem broker
	 * @param port              the optional port to connect to,potherwise
	 *                          defaultport is used, based on thetype ofbroker
	 * @param comSystemConsumer a consumer that receives the ComSystem ready to use
	 * @param rate              the optional time acceleration rate
	 * @return the {@link Thread} that is running the {@link EventLoop}.
	 */
	public static Thread runSimulator(String hostName, Optional<Integer> port,
			final Consumer<ComSystem> comSystemConsumer, final Integer rate) {
		final EventLoop eventLoop;

		if (rate != null) {
			final TimeFake timeFake = new TimeFake(rate);
			eventLoop = new FakedEventLoop(timeFake);
		} else {
			eventLoop = new StandardEventLoop();
		}

		final Thread thread = new Thread(eventLoop, "MARS Event Loop");
		thread.start();

		eventLoop.exec(() -> {
			ComSystem tryComSystem;
			while (true) {
				try {
					System.out.println("Connecting to " + hostName + port.map(p -> " port " + p).orElse(""));
					tryComSystem = new RabbitComSystem(hostName, port.orElse(null), new JacksonJsonConverter());
					break;
				} catch (final Exception e) {
					System.err.println("Cannot connect to: " + hostName + ". Retrying in 5s");
					Thread.sleep(5000);
				}
			}
			final ComSystem comSystem = tryComSystem;
			El.addQuitEvent(() -> {
				comSystem.close();
			});
			comSystemConsumer.accept(comSystem);
		});

		return thread;
	}

	public static SharedConfiguration loadSharedConf() throws IOException {
		try (var r = new FileReader("shared.json")) {
			return new Gson().fromJson(r, SharedConfiguration.class);
		} catch (final FileNotFoundException e) {
			return new SharedConfiguration();
		}
	}
}
