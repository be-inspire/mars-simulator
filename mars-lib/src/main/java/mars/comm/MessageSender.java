package mars.comm;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.cellply.invosys.agent.OutgoingInvocation;

import eventloop.ElUtils;
import eventloop.EventLoop.Event;

public class MessageSender<T> {

	private final Supplier<OutgoingInvocation<T>> send;
	private final Consumer<T> onDone;
	private final Consumer<Throwable> onError;
	private final Event onTimeout;
	private final long timeoutMillis;

	public MessageSender(Supplier<OutgoingInvocation<T>> send, Consumer<T> onDone, Consumer<Throwable> onError,
			Event onTimeout, long timeoutMillis) {
		this.send = send;
		this.onDone = onDone;
		this.onError = onError;
		this.onTimeout = onTimeout;
		this.timeoutMillis = timeoutMillis;
	}

	public void send() {
		ElUtils.process(send.get(), timeoutMillis, onDone, onError, onTimeout);
	}
}
