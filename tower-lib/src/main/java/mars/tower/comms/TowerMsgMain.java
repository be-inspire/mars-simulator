package mars.tower.comms;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.cellply.invosys.ComSystem;
import com.cellply.invosys.agent.CallContext;
import com.cellply.invosys.converters.JacksonJsonConverter;
import com.cellply.invosys.rabbitmq.RabbitComSystem;

import eventloop.El;
import eventloop.EventLoop;
import eventloop.StandardEventLoop;
import mars.comm.test.ConsoleLineReaderForEventLoop;
import mars.comm.test.MessageSenderForTest;
import mars.comm.test.MessageSending;
import mars.messages.Anomaly;
import mars.messages.AnomalyIndication;
import mars.messages.CannotStartPsException;
import mars.messages.DroneLandedConfirm;
import mars.messages.DroneLandedIndication;
import mars.messages.GeoCoord;
import mars.messages.MessageNames;
import mars.messages.PlatformAssignmentIndication;
import mars.messages.PlatformAvailabilityIndication;
import mars.messages.PlatformCylinderEnterRequest;
import mars.messages.PlatformCylinderEnterResponse;
import mars.messages.PlatformCylinderLeftConfirm;
import mars.messages.PlatformCylinderLeftIndication;
import mars.messages.PlatformQuitRequest;
import mars.messages.PlatformQuitResponse;
import mars.messages.PlatformReachabilityConfirm;
import mars.messages.PlatformReachabilityIndication;
import mars.messages.PlatformStatusConfirm;
import mars.messages.PlatformStatusIndication;
import mars.messages.PlatformStatusRequest;
import mars.messages.PsAbortConfirm;
import mars.messages.PsAbortIndication;
import mars.messages.PsCompletedConfirm;
import mars.messages.PsCompletedIndication;
import mars.messages.PsDemandRequest;
import mars.messages.PsDemandResponse;
import mars.messages.PsPlatformAvailability;
import mars.messages.ReadyPsRequest;
import mars.messages.UnexpectedDroneExcpetion;
import mars.messages.UnexpectedPsStateException;
import mars.tower.TowerMessaging;

public class TowerMsgMain {
	public static void main(String[] args) throws Exception {
		final EventLoop eventLoop = new StandardEventLoop();
		final Thread thread = new Thread(eventLoop, "MARS Event Loop");
		thread.start();

		eventLoop.exec(() -> {
			final RabbitComSystem comSystem = new RabbitComSystem("localhost", new JacksonJsonConverter());
			new TowerMsgMain(comSystem, "TOWER");
			El.addQuitEvent(() -> comSystem.close());
		});
		thread.join();
		System.out.println("Bye");
	}

	private final MessageSending messageSending = new MessageSending(System.out, () -> El.quit());
	private final ConsoleLineReaderForEventLoop reader = new ConsoleLineReaderForEventLoop(El.executor(),
			messageSending::process);

	private final long timeoutMillis = 2000;

	public TowerMsgMain(ComSystem comSystem, String agentName) throws IOException {
		final TowerMessaging m = new StandardTowerMessaging(El.executor(), comSystem.createAgent(agentName),
				platformMessageReceiver, mcMessageReceiver);

		messageSending.add(new MessageSenderForTest<>(MessageNames.PS_ABORT_INDICATION + " -> MC1",
				() -> m.sendPsAbortIndication("MC1", new PsAbortIndication(5)),
				r -> System.out.println("MC1 has responded to a " + MessageNames.PS_ABORT_INDICATION + "\n" + r),
				timeoutMillis));

		messageSending.add(new MessageSenderForTest<>(MessageNames.PS_COMPLETED_INDICATION + " -> MC1",
				() -> m.sendPsCompletedIndication("MC1", new PsCompletedIndication(17, "PLATFORM1")),
				r -> System.out.println("MC1 has responded to a " + MessageNames.PS_COMPLETED_INDICATION + "\n" + r),
				timeoutMillis));

		messageSending.add(new MessageSenderForTest<>(MessageNames.PLATFORM_AVAILABILITY_INDICATION + " -> MC1",
				() -> m.sendPlatformAvailabilityIndication("MC1",
						new PlatformAvailabilityIndication(15, createPsPlatformAvailabilities())),
				r -> System.out
						.println("MC1 has responded to a " + MessageNames.PLATFORM_AVAILABILITY_INDICATION + "\n" + r),
				timeoutMillis));

		messageSending.add(new MessageSenderForTest<>(MessageNames.PLATFORM_ASSIGNMENT_INDICATION + " -> MC1",
				() -> m.sendPlatformAssignmentIndication("MC1",
						new PlatformAssignmentIndication(18, "PLATFORM7", Instant.now())),
				r -> System.out
						.println("MC1 has responded to a " + MessageNames.PLATFORM_ASSIGNMENT_INDICATION + "\n" + r),
				timeoutMillis));

		messageSending.add(new MessageSenderForTest<>(MessageNames.PS_ABORT_INDICATION + " -> PL1",
				() -> m.sendPsAbortIndication("PL1", new PsAbortIndication(5)),
				r -> System.out.println("PL1 has responded to a " + MessageNames.PS_ABORT_INDICATION + "\n" + r),
				timeoutMillis));

		messageSending.add(new MessageSenderForTest<>(MessageNames.PLATFORM_STATUS_REQUEST + " -> PL1",
				() -> m.sendPlatformStatusRequest("PL1", new PlatformStatusRequest()),
				r -> System.out.println("PL1 has responded to a " + MessageNames.PLATFORM_STATUS_REQUEST + "\n" + r),
				timeoutMillis));

		messageSending.add(new MessageSenderForTest<>(MessageNames.READY_PS_REQUEST + " -> PL1",
				() -> m.sendReadyPsRequest("PL1", new ReadyPsRequest(13, 4)),
				r -> System.out.println("PL1 has responded to a " + MessageNames.READY_PS_REQUEST + "\n" + r),
				timeoutMillis));

		messageSending.add(new MessageSenderForTest<>(MessageNames.DRONE_LANDED_INDICATION + " -> PL1",
				() -> m.sendDroneLandedIndication("PL1", new DroneLandedIndication(21, "PLATFORM7", "drone XYZ")),
				r -> System.out.println("PL1 has responded to a " + MessageNames.DRONE_LANDED_INDICATION + "\n" + r),
				timeoutMillis));

		messageSending.add(new MessageSenderForTest<>(MessageNames.ANOMALY_INDICATION + " -> PL1",
				() -> m.sendAnomalyIndication("PL1", new AnomalyIndication(EnumSet.of(Anomaly.CYLINDER_BUSY))),
				r -> System.out.println("PL1 has responded to a " + MessageNames.ANOMALY_INDICATION + "\n" + r),
				timeoutMillis));

		reader.start();
		messageSending.dumpMessageSenders();
	}

	private final PlatformMessageReceiver platformMessageReceiver = new PlatformMessageReceiver() {

		@Override
		public void onPlatformStatus(CallContext<PlatformStatusIndication> callCtx) {
			final PlatformStatusIndication p = callCtx.params();
			System.out.println("RECV: from " + callCtx.callingAgentId() + " " + MessageNames.PLATFORM_STATUS_INDICATION
					+ "\n" + p);
			callCtx.resolve(new PlatformStatusConfirm());
		}

		@Override
		public void onPsCompleted(CallContext<PsCompletedIndication> callCtx) {
			final PsCompletedIndication p = callCtx.params();
			System.out.println(
					"RECV: from " + callCtx.callingAgentId() + " " + MessageNames.PS_COMPLETED_INDICATION + "\n" + p);
			callCtx.resolve(new PsCompletedConfirm());
		}

		@Override
		public void onPlatformQuit(CallContext<PlatformQuitRequest> callCtx) {
			final PlatformQuitRequest p = callCtx.params();
			System.out.println(
					"RECV: from " + callCtx.callingAgentId() + " " + MessageNames.PLATFORM_QUIT_REQUEST + "\n" + p);
			callCtx.resolve(new PlatformQuitResponse(21, El.now()));
		}
	};
	protected int psId;

	private final McMessageReceiver mcMessageReceiver = new McMessageReceiver() {

		@Override
		public void onPsDemand(CallContext<PsDemandRequest> callCtx) {
			final PsDemandRequest p = callCtx.params();
			System.out.println(
					"RECV: from " + callCtx.callingAgentId() + " " + MessageNames.PS_DEMAND_REQUEST + "\n" + p);
			callCtx.resolve(new PsDemandResponse(p.requestId(), psId++, createPsPlatformAvailabilities()));
		}

		@Override
		public void onPsAbort(CallContext<PsAbortIndication> callCtx) {
			final PsAbortIndication p = callCtx.params();
			System.out.println(
					"RECV: from " + callCtx.callingAgentId() + " " + MessageNames.PS_ABORT_INDICATION + "\n" + p);
			callCtx.resolve(new PsAbortConfirm());
		}

		@Override
		public void onPlatformCylinderEnter(CallContext<PlatformCylinderEnterRequest> callCtx) {
			final PlatformCylinderEnterRequest p = callCtx.params();
			System.out.println("RECV: " + MessageNames.PS_ABORT_INDICATION + "\n" + p);
			final boolean ok = Math.random() < .5;
			if (ok)
				callCtx.resolve(new PlatformCylinderEnterResponse());
			else
				callCtx.fail(new UnexpectedPsStateException("Unexpected PS state"));
		}

		@Override
		public void onDroneLanded(CallContext<DroneLandedIndication> callCtx) {
			final DroneLandedIndication p = callCtx.params();
			System.out.println("RECV: " + MessageNames.DRONE_LANDED_INDICATION + "\n" + p);
			final var ok = Math.random() < .5;
			if (ok)
				callCtx.resolve(new DroneLandedConfirm());
			else {
				if (Math.random() < .5)
					callCtx.fail(new CannotStartPsException("something went wrong"));
				else
					callCtx.fail(new UnexpectedDroneExcpetion("something went wrong"));
			}
		}

		@Override
		public void onPlatformCylinderLeft(CallContext<PlatformCylinderLeftIndication> callCtx) {
			final PlatformCylinderLeftIndication p = callCtx.params();
			System.out.println("RECV: " + MessageNames.PLATFORM_CYLINDER_LEFT_INDICATION + "\n" + p);
			callCtx.resolve(new PlatformCylinderLeftConfirm());
		}

		@Override
		public void onPlatformReachability(CallContext<PlatformReachabilityIndication> callCtx) {
			final PlatformReachabilityIndication p = callCtx.params();
			System.out.println("RECV: " + MessageNames.PLATFORM_REACHABILITY_INDICATION + "\n" + p);
			callCtx.resolve(new PlatformReachabilityConfirm());

		}

	};

	protected List<PsPlatformAvailability> createPsPlatformAvailabilities() {
		final List<PsPlatformAvailability> l = new ArrayList<PsPlatformAvailability>();
		l.add(new PsPlatformAvailability("PLATFORM1", new GeoCoord(8.34, 44.22)));
		l.add(new PsPlatformAvailability("PLATFORM2", new GeoCoord(8.356, 44.102)));
		return l;
	}

}
