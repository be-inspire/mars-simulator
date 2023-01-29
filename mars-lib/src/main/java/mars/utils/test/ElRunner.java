package mars.utils.test;

import eventloop.EventLoop;
import eventloop.EventLoop.Event;

public class ElRunner {

	private final Thread thread;
	private final EventLoop eventLoop;
	private Throwable thrown;

	public ElRunner(EventLoop eventLoop) {
		this.eventLoop = eventLoop;
		eventLoop.setOnThrown(this::onThrown);
		thread = new Thread(eventLoop);
		thread.setDaemon(true);
	}

	public void runEventAndWait(Event event) throws Exception {
		eventLoop.exec(event);
		thread.start();
		eventLoop.waitEmpty();
		checkThrown();
	}

	private boolean onThrown(Throwable t) {
		this.thrown = t;
		return false;
	}

	public void checkThrown() throws Exception {
		if (thrown != null) {
			if (thrown instanceof Exception) {
				throw (Exception) thrown;
			} else
				throw new RuntimeException("Unexpected throwable", thrown);
		}
	}

}
