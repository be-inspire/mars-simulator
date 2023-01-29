package mars.tower.test;

@FunctionalInterface
public interface ExceptingRunnable {
	void run() throws Exception;
}
