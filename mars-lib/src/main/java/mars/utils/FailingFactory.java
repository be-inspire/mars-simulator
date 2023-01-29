package mars.utils;

@FunctionalInterface
public interface FailingFactory<T> {
	T create() throws Exception;
}