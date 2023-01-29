package eventloop;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import eventloop.EventLoop.Event;

public class ElUtils {
	private ElUtils() {

	}

	public static <T> void process(final CompletionStage<T> stage, long timeoutMillis, Consumer<T> onDone,
			Consumer<Throwable> onError, Event onTimeout) {
		if (stage == null)
			throw new NullPointerException("Invocation is null");
		final AtomicBoolean cancelled = new AtomicBoolean();
		final Timeout timeout = El.setTimeout(timeoutMillis, () -> {
			cancelled.set(true);
			onTimeout.run();
		});
		final var inLoopExecutor = El.executor();
		stage.thenAccept(t -> inLoopExecutor.accept(() -> {
			if (cancelled.get())
				return;
			timeout.cancel();
			onDone.accept(t);
		})).exceptionally(t -> {
			inLoopExecutor.accept(() -> {
				if (cancelled.get())
					return;
				timeout.cancel();
				onError.accept(t);
			});
			return null;
		});
	}

}
