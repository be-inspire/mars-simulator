package mars.platform.comms;

import java.io.IOException;
import java.time.Duration;
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
import mars.messages.AnomalyConfirm;
import mars.messages.AnomalyIndication;
import mars.messages.CannotReadyPayload;
import mars.messages.CannotStartPsException;
import mars.messages.DroneLandedConfirm;
import mars.messages.DroneLandedIndication;
import mars.messages.GeoCoord;
import mars.messages.MessageNames;
import mars.messages.PayloadBay;
import mars.messages.PlatformAlarms;
import mars.messages.PlatformQuitRequest;
import mars.messages.PlatformStatus;
import mars.messages.PlatformStatusIndication;
import mars.messages.PlatformStatusRequest;
import mars.messages.PlatformStatusResponse;
import mars.messages.PsAbortConfirm;
import mars.messages.PsAbortIndication;
import mars.messages.PsCompletedIndication;
import mars.messages.ReadyPsRequest;
import mars.messages.ReadyPsResponse;
import mars.messages.RestoringPayload;
import mars.messages.UnexpectedDroneExcpetion;
import mars.messages.UnexpectedPsStateException;

public class PlatformMsgMain {
	public static void main(String[] args) throws Exception {
		final EventLoop eventLoop = new StandardEventLoop();
		final Thread thread = new Thread(eventLoop, "MARS Event Loop");
		thread.start();

		eventLoop.exec(() -> {
			final RabbitComSystem comSystem = new RabbitComSystem("localhost", new JacksonJsonConverter());
			new PlatformMsgMain(comSystem, "PL1");
			El.addQuitEvent(() -> {
				comSystem.close();
			});
		});
		thread.join();
		System.out.println("Bye");
	}

	final MessageSending messageSending = new MessageSending(System.out, () -> El.quit());
	private final long timeoutMillis = 2000;

	public PlatformMsgMain(ComSystem comSystem, String agentName) throws IOException {
		final StandardPlatformMessaging m = new StandardPlatformMessaging(El.executor(),
				comSystem.createAgent(agentName), messageReceiver);
		m.setTowerName("TOWER");

		messageSending.add(new MessageSenderForTest<>(MessageNames.PLATFORM_STATUS_INDICATION + " -> TOWER",
				() -> m.sendPlatformStatusIndication(new PlatformStatusIndication(makePartialPlatformStatus())),
				r -> System.out
						.println("TOWER has responded to a " + MessageNames.PLATFORM_STATUS_INDICATION + "\n" + r),
				timeoutMillis));

		messageSending.add(new MessageSenderForTest<>(MessageNames.PS_COMPLETED_INDICATION + " -> TOWER",
				() -> m.sendPsCompletedIndication(new PsCompletedIndication(11, "PLATFORM1")),
				r -> System.out.println("TOWER has responded to a " + MessageNames.PS_COMPLETED_INDICATION + "\n" + r),
				timeoutMillis));

		messageSending.add(new MessageSenderForTest<>(MessageNames.PLATFORM_QUIT_REQUEST + " -> TOWER",
				() -> m.sendPlatformQuitRequest(new PlatformQuitRequest("PLATFORM1")),
				r -> System.out.println("TOWER has responded to a " + MessageNames.PLATFORM_QUIT_REQUEST + "\n" + r),
				timeoutMillis));

		final ConsoleLineReaderForEventLoop reader = new ConsoleLineReaderForEventLoop(El.executor(),
				messageSending::process);
		reader.start();
		messageSending.dumpMessageSenders();
	}

	private final TowerMessageReceiver messageReceiver = new TowerMessageReceiver() {

		@Override
		public void onPlatformStatus(CallContext<PlatformStatusRequest> callCtx) {
			System.out.println("RECV: " + MessageNames.PLATFORM_STATUS_REQUEST + "\n" + callCtx.params());
			callCtx.resolve(new PlatformStatusResponse(new GeoCoord(8.1, 44.2), makePlatformStatus()));
		}

		@Override
		public void onReadyPs(CallContext<ReadyPsRequest> callCtx) {
			final ReadyPsRequest p = callCtx.params();
			System.out.println("RECV: " + MessageNames.READY_PS_REQUEST + "\n" + p);
			final var ok = Math.random() < .5;
			if (ok)
				callCtx.resolve(new ReadyPsResponse());
			else {
				if (Math.random() < .5)
					callCtx.fail(new UnexpectedPsStateException("Unexpected PS state"));
				else
					callCtx.fail(new CannotReadyPayload("Problemsonnready PS"));
			}

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
		public void onPsAbort(CallContext<PsAbortIndication> callCtx) {
			final PsAbortIndication p = callCtx.params();
			System.out.println("RECV: " + MessageNames.PS_ABORT_INDICATION + "\n" + p);
			callCtx.resolve(new PsAbortConfirm());
		}

		@Override
		public void onAnomalyIndicated(CallContext<AnomalyIndication> callCtx) {
			final AnomalyIndication p = callCtx.params();
			System.out.println("RECV: " + MessageNames.ANOMALY_INDICATION + "\n" + p);
			callCtx.resolve(new AnomalyConfirm());
		}
	};

	protected PlatformStatus makePlatformStatus() {
		final List<PayloadBay> l = new ArrayList<>();
		l.add(new PayloadBay(1, new RestoringPayload("Pl100", "PT1", .86, El.now().plus(Duration.ofMinutes(5))),
				10_000));
		l.add(new PayloadBay(2, new RestoringPayload("Pl223", "PT1", 1, null), 25_000));
		l.add(new PayloadBay(3, new RestoringPayload("Pl315", "PT1", .12, El.now().plus(Duration.ofMinutes(150))),
				12_000));

		l.add(new PayloadBay(4, null, timeoutMillis));
		l.add(new PayloadBay(5, new RestoringPayload("Pl525", "PT2", .22, El.now().plus(Duration.ofMinutes(50))),
				22_000));
		l.add(new PayloadBay(6, new RestoringPayload("Pl164", "PT2", .1, El.now().plus(Duration.ofMinutes(24))),
				9_000));
		l.add(new PayloadBay(7, new RestoringPayload("Pl763", "PT2", .99, El.now().plus(Duration.ofMinutes(1))),
				11_000));
		return new PlatformStatus(null, new PlatformAlarms(false, false), l, 90_000);
	}

	private PlatformStatus makePartialPlatformStatus() {
		final List<PayloadBay> l = new ArrayList<>();
		l.add(new PayloadBay(3, new RestoringPayload("Pl177", "PT1", .45, El.now().plus(Duration.ofMinutes(61))),
				15_000));

		l.add(new PayloadBay(4, new RestoringPayload("Pl833", "PT2", .01, El.now().plus(Duration.ofMinutes(220))),
				12_000));
		l.add(new PayloadBay(5, new RestoringPayload("Pl728", "PT2", .88, El.now().plus(Duration.ofMinutes(6))),
				9_000));
		return new PlatformStatus(null, new PlatformAlarms(false, true), l, 90_000);
	}
}
