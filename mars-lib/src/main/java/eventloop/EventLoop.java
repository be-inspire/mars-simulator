package eventloop;

import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Function;

public interface EventLoop extends Runnable {

	@FunctionalInterface
	static interface Event {
		void run() throws Throwable;
	}

	void exec(Event e);

	boolean isRunning();

	void waitEmpty() throws InterruptedException;

	boolean isEmpty();

	Timeout setTimeout_(long millis, Event c);

	Instant now_();

	void quit_();

	void addQuitEvent_(Event event);

	Consumer<Event> executor_();

	void setOnThrown(Function<Throwable, Boolean> onThrown);
}
