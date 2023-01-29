package mars.messages;

public class CannotReadyPayload extends Exception {

	private static final long serialVersionUID = -9030885975059742542L;

	public CannotReadyPayload(String message) {
		super(message);
	}

	public CannotReadyPayload(Throwable cause) {
		super(cause);
	}

	public CannotReadyPayload(String message, Throwable cause) {
		super(message, cause);
	}
}
