package mars.comm.test;

import java.util.Scanner;
import java.util.function.Consumer;

import eventloop.EventLoop.Event;

public class ConsoleLineReaderForEventLoop extends Thread {
	private final Scanner scanner = new Scanner(System.in);
	private final Consumer<String> c;
	private final Consumer<Event> executor;

	public ConsoleLineReaderForEventLoop(Consumer<Event> executor, Consumer<String> c) {
		this.executor = executor;
		this.c = c;
	}

	@Override
	public void run() {
		while (true) {
			final var l = scanner.nextLine();
			executor.accept(() -> c.accept(l));
			if (l.trim().equals("q"))
				break;
		}
	}

	public void close() {
		scanner.close();
	}
}
