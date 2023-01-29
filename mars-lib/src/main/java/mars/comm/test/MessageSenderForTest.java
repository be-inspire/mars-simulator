package mars.comm.test;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.cellply.invosys.agent.OutgoingInvocation;

import eventloop.EventLoop.Event;
import mars.comm.MessageSender;

public class MessageSenderForTest<T> extends MessageSender<T> {

	private final String desc;

	public MessageSenderForTest(String desc, Supplier<OutgoingInvocation<T>> send, Consumer<T> onDone,
			long timoutMillis) {
		super(send, onDone, onError, onTimeout, timoutMillis);
		this.desc = desc;
	}

	private static final Event onTimeout = () -> {
		System.err.println("Call timed out!");
	};

	private static final Consumer<Throwable> onError = t -> {
		System.err.println("Call got an error");
		t.printStackTrace();
	};

	@Override
	public String toString() {
		return desc;
	}
}
