package mars.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class Repo<T> {
	public interface Index<T, I> {
		T get(I key);
	}

	private final List<T> items = new ArrayList<>();
	private final Map<String, HashIndex<?>> indexes = new HashMap<>();

	public void add(T item) {
		items.add(item);
		for (final HashIndex<?> i : indexes.values()) {
			i.add(item);
		}
	}

	public <I> Index<T, I> addIndex(String name, Function<T, I> f) {
		final HashIndex<I> index = new HashIndex<>(name, f);
		indexes.put(name, index);
		for (final T t : items) {
			index.add(t);
		}
		return index;
	}

	class HashIndex<K> implements Index<T, K> {

		private final Function<T, K> f;
		private final String name;
		private final Map<K, T> items = new HashMap<>();

		private HashIndex(final String name, final Function<T, K> f) {
			this.name = name;
			this.f = f;
		}

		private void add(T item) {
			final K key = f.apply(item);
			if (items.containsKey(key))
				throw new IllegalStateException("An iteem mapping to " + key + " already exists in index " + name);
			items.put(key, item);
		}

		@Override
		public T get(K key) {
			return items.get(key);
		}
	}
}