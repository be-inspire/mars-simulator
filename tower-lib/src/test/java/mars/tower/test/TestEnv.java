package mars.tower.test;

import static org.mockito.Mockito.when;

import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;

import com.cellply.invosys.agent.OutgoingInvocation;

import eventloop.EventLoop.Event;
import mars.heartbeat.AgentLifecycleListener;
import mars.messages.PlatformAssignmentConfirm;
import mars.messages.PlatformAssignmentIndication;
import mars.tower.PitStop;
import mars.tower.PlatformStatus;
import mars.tower.TowerMessaging;
import mars.tower.comms.McMessageReceiver;
import mars.tower.comms.PlatformMessageReceiver;
import mars.utils.test.ElTestSupport;

public class TestEnv extends BaseTestEnv {

	public TestEnv(TowerMessaging tm) {
		super();
		this.tower = tm;
	}

	private final ElTestSupport elSupport = new ElTestSupport();

	public final TowerMessaging tower;
	public PlatformMessageReceiver pltSent;
	public McMessageReceiver mcSent;
	public AgentLifecycleListener all;
	final TestWorldModel worldModel = new TestWorldModel();

	public TowerMessaging messagingMaker(PlatformMessageReceiver pmr, McMessageReceiver mmr) {
		this.pltSent = pmr;
		this.mcSent = mmr;
		return tower;
	}

	public void allConsumer(AgentLifecycleListener all) {
		this.all = all;
	}

	public void runEventAndWait(long millis, Event event) throws Exception {
		elSupport.runEventAndWait(millis, event);
	}

	public PitstopChecker assertPitstop(int pitStopId) {
		return assertPitstop(findPitstop(pitStopId));
	}

	private PitStop findPitstop(int pitStopId) {
		return worldModel.getPitStop(pitStopId)
				.or(() -> worldModel.getBuriedPitStops().stream().filter(p -> p.id == pitStopId).findFirst()).get();
	}

	public PitstopChecker assertPitstop(PitStop pitStop) {
		return new PitstopChecker(pitStop);
	}

	public void setPitStops(PitStop... pitStops) {
		worldModel.setPitStops(pitStops);
	}

	public void setPlatformStatus(PlatformStatus... platformsStatus) {
		worldModel.setPlatformStatus(platformsStatus);
	}

	void mcWillConfirmAssignment(int psId, String mcId, String pltId, long l, ChronoUnit unit) {
		final PlatformAssignmentIndication as = new PlatformAssignmentIndication(psId, pltId, after(l, unit));
		when(tower.sendPlatformAssignmentIndication(mcId, as))
				.thenReturn(OutgoingInvocation.completed(new PlatformAssignmentConfirm()));
	}

	public BayChecker assertBay(String pltId, int bayId) {
		final var bay = worldModel.getPlatformStatus(pltId).payloadBays().stream().filter(b -> b.bayId() == bayId)
				.findFirst().orElseGet(() -> {
					throw new NoSuchElementException("Bay in platform not found: " + pltId + "-" + bayId);
				});
		return new BayChecker(bay);
	}
}
