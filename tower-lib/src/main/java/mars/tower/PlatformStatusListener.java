package mars.tower;

public interface PlatformStatusListener {
	void platformStatusUpdate(PlatformStatus old, PlatformStatus current);

	void psStatusUpdate(PitStop ps);
}
