package mars.tower;

public class NoAvailabilityException extends Exception {

	private static final long serialVersionUID = -8757361223689679082L;

	public NoAvailabilityException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public NoAvailabilityException(String message) {
		super(message);
	}

}
