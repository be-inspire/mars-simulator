package eventloop;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StandardEventLoop implements EventLoop {
	private final static Logger LOGGER = LoggerFactory.getLogger(StandardEventLoop.class);

	private final BlockingQueue<Event> queue = new LinkedBlockingQueue<Event>();
	private final Timer timer = new Timer();
	private Function<Throwable, Boolean> onThrown = t -> {
		t.printStackTrace();
		return false;
	};
	private Thread runningThread;
	private boolean quit;
	private final List<Event> quitEvents = new LinkedList<>();

	private Instant currentEventTime;
	private int tasks;

	@Override
	public Timeout setTimeout_(long millis, Event c) {
		if (millis < 0)
			throw new IllegalArgumentException("Timeout cannot be negative");
		if (millis == 0) {
			addToQueue(c);
			return Timeout.NULL;
		} else {
			final TimerTask task = new ExecutingTask(c);
			timer.schedule(task, millis);
			return new CancellingTimeout(task);
		}
	}

	@Override
	public Instant now_() {
		return currentEventTime;
	}

	@Override
	public void quit_() {
		if (!isElThread())
			throw new RuntimeException("quit() can be called only FROM the event loop thread");
		queue.addAll(quitEvents);
		quit = true;

	}

	@Override
	public void addQuitEvent_(Event event) {
		quitEvents.add(event);
	}

	private Event currentEvent;

	private boolean started;

	public StandardEventLoop() {
	}

	@Override
	public void setOnThrown(Function<Throwable, Boolean> onThrown) {
		this.onThrown = onThrown;
	}

	@Override
	public void run() {
		this.runningThread = Thread.currentThread();
		El.loops.put(this.runningThread, this);
		started = true;
		try {
			while (!Thread.currentThread().isInterrupted()) {
				if (quit && queue.isEmpty())
					break;
				try {
					currentEvent = queue.take();
				} catch (final InterruptedException e) {
					e.printStackTrace();
					Thread.currentThread().interrupt();
					continue;
				}
				try {
					currentEventTime = Instant.now();
					currentEvent.run();
				} catch (final Throwable t) {
					if (onThrown(t))
						return;
				} finally {
					currentEvent = null;
				}
			}
		} finally {
			this.runningThread = null;
			El.loops.remove(this.runningThread);
			LOGGER.info("BYE");
			timer.cancel();
		}
	}

	protected boolean onThrown(final Throwable t) {
		return onThrown.apply(t);
	}

	@Override
	public void exec(Event r) {
		if (isElThread())
			throw new RuntimeException("exec() can be called only OUTSIDE the event loop thread");

		addToQueue(r);
	}

	private void addToQueue(Event r) {
		if (quit)
			return;
		LOGGER.trace("Adding " + r);
		queue.add(r);
	}

	private boolean isElThread() {
		return Thread.currentThread().equals(runningThread);
	}

	private final class CancellingTimeout implements Timeout {
		private final TimerTask task;

		private CancellingTimeout(TimerTask task) {
			this.task = task;
		}

		@Override
		public void cancel() {
			if (!isElThread())
				throw new RuntimeException("cancel() can be called only FROM the event loop thread");
			task.cancel();
		}
	}

	private final class ExecutingTask extends TimerTask {
		private final Event c;
		private boolean cancelled;

		private ExecutingTask(Event c) {
			tasks++;
			this.c = c;
		}

		@Override
		public boolean cancel() {
			this.cancelled = true;
			tasks--;
			return super.cancel();
		}

		@Override
		public void run() {
			addToQueue(() -> {
				if (cancelled)
					return;
				tasks--;
				addToQueue(c);
			});
		}
	}

	@Override
	public boolean isRunning() {
		return runningThread != null;
	}

	@Override
	public void waitEmpty() throws InterruptedException {
		if (isElThread())
			throw new RuntimeException("waitEmpty() can be called only OUTSIDE the event loop thread");
		while (!started) {
			Thread.sleep(10);// TODO ugly
		}
		while (!isEmpty() && isRunning()) {
			Thread.sleep(10);// TODO ugly
		}
	}

	@Override
	public boolean isEmpty() {
		return queue.size() == 0 && currentEvent == null && tasks == 0;
	}

	@Override
	public Consumer<Event> executor_() {
		return e -> setTimeout_(0, e);
	}
}
