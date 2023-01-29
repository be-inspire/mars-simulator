package mars.comm.test;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import eventloop.El;
import eventloop.EventLoop.Event;
import mars.comm.MessageSender;

public class MessageSending {
	private final List<MessageSender<?>> messageSenders = new ArrayList<>();
	private final PrintStream ps;
	private final Event quitEvent;

	public MessageSending(PrintStream ps, Event quitEvent) {
		this.ps = ps;
		this.quitEvent = quitEvent;
	}

	public void dumpMessageSenders() {
		for (int i = 0; i < messageSenders.size(); i++) {
			ps.println(i + " - " + messageSenders.get(i));
		}
		ps.println("q - QUIT");
	}

	public void add(MessageSender<?> r) {
		messageSenders.add(r);
	}

	public void process(String l) {
		l = l.trim();
		if (l.length() == 0)
			dumpMessageSenders();
		else if ("q".equals(l))
			El.setTimeout(0, quitEvent);
		else {
			final int index = Integer.parseInt(l);
			final var sender = messageSenders.get(index);
			sender.send();
		}
	}
}
