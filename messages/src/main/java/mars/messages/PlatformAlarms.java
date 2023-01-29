package mars.messages;

public record PlatformAlarms(boolean flangeUnavailable, boolean cylinderBusy) {
	public boolean isUnavailable() {
		return flangeUnavailable || cylinderBusy;
	}
}
