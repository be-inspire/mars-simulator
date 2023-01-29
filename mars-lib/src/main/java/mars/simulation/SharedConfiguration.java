package mars.simulation;

public class SharedConfiguration {
	public Integer timeRate;
	public int heartbeatPeriodInSeconds = 5;
	public long commsTimeoutMillis = 10_000;

	@Override
	public String toString() {
		return "SharedConfiguration [timeRate=" + timeRate + ", heartbeatPeriodInSeconds=" + heartbeatPeriodInSeconds
				+ ", commsTimeoutMillis=" + commsTimeoutMillis + "]";
	}
}
