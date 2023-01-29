package mars.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ListenerNotifier<T> {

	private final List<T> listeners = new ArrayList<>();

	public void addListener(T l) {
		if (l == null)
			throw new NullPointerException();
		listeners.add(l);

	}

	public void notifyListeners(Consumer<T> c) {
		for (final var l : listeners) {
			c.accept(l);
		}
	}

	public void addAllListener(List<T> listeners) {
		this.listeners.addAll(listeners);
	}
}
