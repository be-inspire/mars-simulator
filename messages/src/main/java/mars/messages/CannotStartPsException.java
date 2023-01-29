package mars.messages;

public class CannotStartPsException extends Exception {

	private static final long serialVersionUID = 7992952204686764016L;

	public CannotStartPsException(String message) {
		super(message);
	}

	public CannotStartPsException(Throwable cause) {
		super(cause);
	}

	public CannotStartPsException(String message, Throwable cause) {
		super(message, cause);
	}
}
