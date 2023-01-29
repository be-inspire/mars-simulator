package mars.messages;

public class UnexpectedPsStateException extends Exception {

	private static final long serialVersionUID = -1118063727653964143L;

	public UnexpectedPsStateException(String message) {
		super(message);
	}

	public UnexpectedPsStateException(Throwable cause) {
		super(cause);
	}

	public UnexpectedPsStateException(String message, Throwable cause) {
		super(message, cause);
	}

}
