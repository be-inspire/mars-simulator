package mars.heartbeat;

public interface AgentLifecycleListener {
	void agentLost(String agentName, String category);

	void agentDiscovered(String agentName, String category);

	void agentReturned(String agentName, String category);
}
