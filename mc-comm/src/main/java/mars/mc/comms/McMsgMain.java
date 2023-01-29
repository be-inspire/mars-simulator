package mars.mc.comms;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
import mars.messages.DroneLandedIndication;
import mars.messages.MessageNames;
import mars.messages.PlatformAssignmentConfirm;
import mars.messages.PlatformAssignmentIndication;
import mars.messages.PlatformAvailabilityConfirm;
import mars.messages.PlatformAvailabilityIndication;
import mars.messages.PlatformCylinderEnterRequest;
import mars.messages.PlatformCylinderLeftIndication;
import mars.messages.PlatformReachabilityIndication;
import mars.messages.PsAbortConfirm;
import mars.messages.PsAbortIndication;
import mars.messages.PsCompletedConfirm;
import mars.messages.PsCompletedIndication;
import mars.messages.PsDemandRequest;
import mars.messages.PsDemandResponse;
import mars.messages.PsPlatformReachability;

public class McMsgMain {
	public static void main(String[] args) throws Exception {
		final EventLoop eventLoop = new StandardEventLoop();
		final Thread thread = new Thread(eventLoop, "MARS Event Loop");
		thread.start();

		eventLoop.exec(() -> {
			final RabbitComSystem comSystem = new RabbitComSystem("localhost", new JacksonJsonConverter());
			new McMsgMain(comSystem, "MC1");
			El.addQuitEvent(() -> {
				comSystem.close();
			});
		});
		thread.join();
		System.out.println("Bye");
	}

	final MessageSending messageSending = new MessageSending(System.out, () -> El.quit());
	private final long timeoutMillis = 2000;
	private int requestId = 0;

	public McMsgMain(ComSystem comSystem, String agentName) throws IOException {
		final var m = new StandardMcMessaging(El.executor(), comSystem.createAgent(agentName), messageReceiver);
		m.setTowerName("TOWER");

		messageSending.add(new MessageSenderForTest<>(MessageNames.PS_DEMAND_REQUEST + " -> TOWER",
				() -> m.sendPsDemandRequest(
						new PsDemandRequest(requestId++, "PAYLOAD1", El.now().plus(5, ChronoUnit.MINUTES))),
				(PsDemandResponse r) -> System.out.println("TOWER has responded\n" + r), timeoutMillis));

		messageSending.add(new MessageSenderForTest<>(MessageNames.PS_ABORT_INDICATION + " -> TOWER",
				() -> m.sendPsAbortIndication(new PsAbortIndication(8)),
				(PsAbortConfirm r) -> System.out.println("TOWER has responded\n" + r), timeoutMillis));

		messageSending.add(new MessageSenderForTest<>(MessageNames.PLATFORM_REACHABILITY_INDICATION + " -> TOWER",
				() -> m.sendPlatformReachabilityIndication(
						new PlatformReachabilityIndication(19, createPsPlatformReachabilities())),
				r -> System.out.println("TOWER has responded\n" + r), timeoutMillis));

		messageSending.add(new MessageSenderForTest<>(MessageNames.PLATFORM_CYLINDER_ENTER_REQUEST + " -> TOWER",
				() -> m.sendPlatformCylinderEnterRequest(new PlatformCylinderEnterRequest(19, "PLATFORM6")),
				r -> System.out.println("TOWER has responded\n" + r), timeoutMillis));

		messageSending.add(new MessageSenderForTest<>(MessageNames.DRONE_LANDED_INDICATION + " -> TOWER",
				() -> m.sendDroneLandedIndication(new DroneLandedIndication(20, "PLATRFOMR5", "drone XYZ")),
				r -> System.out.println("TOWER has responded\n" + r), timeoutMillis));

		messageSending.add(new MessageSenderForTest<>(MessageNames.PLATFORM_CYLINDER_LEFT_INDICATION + " -> TOWER",
				() -> m.sendPlatformCylinderLeftIndication(new PlatformCylinderLeftIndication(25, "PLATFOMR8")),
				r -> System.out.println("TOWER has responded\n" + r), timeoutMillis));

		final ConsoleLineReaderForEventLoop reader = new ConsoleLineReaderForEventLoop(El.executor(),
				messageSending::process);
		reader.start();
		messageSending.dumpMessageSenders();
	}

	private final TowerMessageReceiver messageReceiver = new TowerMessageReceiver() {

		@Override
		public void onPsCompleted(CallContext<PsCompletedIndication> callCtx) {
			final PsCompletedIndication p = callCtx.params();
			System.out.println("RECV: " + MessageNames.PS_COMPLETED_INDICATION + "\n" + p);
			final int millis = Math.random() < .5 ? 3000 : 0;
			System.out.println("Respondin with delay millis: " + millis);
			El.setTimeout(millis, () -> callCtx.resolve(new PsCompletedConfirm()));
		}

		@Override
		public void onPsAbort(CallContext<PsAbortIndication> callCtx) {
			final PsAbortIndication p = callCtx.params();
			System.out.println("RECV: " + MessageNames.PS_ABORT_INDICATION + "\n" + p);
			callCtx.resolve(new PsAbortConfirm());
		}

		@Override
		public void onPlatformAssignment(CallContext<PlatformAssignmentIndication> callCtx) {
			final PlatformAssignmentIndication p = callCtx.params();
			System.out.println("RECV: " + MessageNames.PLATFORM_ASSIGNMENT_INDICATION + "\n" + p);
			callCtx.resolve(new PlatformAssignmentConfirm());
		}

		@Override
		public void onPlatformAvailability(CallContext<PlatformAvailabilityIndication> callCtx) {
			final PlatformAvailabilityIndication p = callCtx.params();
			System.out.println("RECV: " + MessageNames.PLATFORM_AVAILABILITY_INDICATION + "\n" + p);
			callCtx.resolve(new PlatformAvailabilityConfirm(p.psId(), createPsPlatformReachabilities()));
		}
	};

	private List<PsPlatformReachability> createPsPlatformReachabilities() {
		final List<PsPlatformReachability> l = new ArrayList<>();
		l.add(new PsPlatformReachability("PLATFORM1", El.now().plus(Duration.ofMinutes(4)),
				El.now().plus(Duration.ofMinutes(12))));
		l.add(new PsPlatformReachability("PLATFORM3", El.now().plus(Duration.ofMinutes(6)),
				El.now().plus(Duration.ofMinutes(9))));
		return l;
	}
};
