package eventloop;

public interface Timeout {
	public final static Timeout NULL = new Timeout() {
		@Override
		public void cancel() {
		}

		@Override
		public String toString() {
			return "NULL";
		}
	};

	void cancel();

}
