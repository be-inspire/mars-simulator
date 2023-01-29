package eventloop;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import eventloop.EventLoop.Event;

public class El {

	final static Map<Thread, EventLoop> loops = new HashMap<>();

	public static Timeout setTimeout(long millis, Event c) {
		return getLoop("setTimeout()").setTimeout_(millis, c);
	}

	public static Instant now() {
		return getLoop("now()").now_();
	}

	public static void quit() {
		getLoop("quit()").quit_();
	}

	public static void addQuitEvent(Event event) {
		getLoop("addQuitEvent()").addQuitEvent_(event);
	}

	public static boolean inEl() {
		return loops.get(Thread.currentThread()) != null;
	}

	private static EventLoop getLoop(String name) {
		final var el = loops.get(Thread.currentThread());
		if (el == null)
			throw new RuntimeException(name + "can be called only FROM an event loop thread");
		return el;
	}

	public static Consumer<Event> executor() {
		return getLoop("executor()").executor_();
	}
}
