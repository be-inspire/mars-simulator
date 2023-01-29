package mars.tower.test;

import static java.time.Duration.ZERO;
import static java.time.Duration.of;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cellply.invosys.agent.MockCallContext;
import com.cellply.invosys.agent.OutgoingInvocation;

import eventloop.El;
import mars.agent.OnlineStatus;
import mars.messages.Anomaly;
import mars.messages.AnomalyConfirm;
import mars.messages.AnomalyIndication;
import mars.messages.CannotReadyPayload;
import mars.messages.DroneLandedConfirm;
import mars.messages.DroneLandedIndication;
import mars.messages.PayloadBay;
import mars.messages.PlatformCylinderEnterRequest;
import mars.messages.PlatformCylinderEnterResponse;
import mars.messages.PlatformCylinderLeftConfirm;
import mars.messages.PlatformCylinderLeftIndication;
import mars.messages.PlatformReachabilityConfirm;
import mars.messages.PlatformReachabilityIndication;
import mars.messages.PlatformStatusIndication;
import mars.messages.PlatformStatusRequest;
import mars.messages.PlatformStatusResponse;
import mars.messages.PsAbortConfirm;
import mars.messages.PsAbortIndication;
import mars.messages.PsCompletedConfirm;
import mars.messages.PsCompletedIndication;
import mars.messages.PsDemandRequest;
import mars.messages.PsDemandResponse;
import mars.messages.PsPlatformAvailability;
import mars.messages.PsState;
import mars.messages.ReadyPsRequest;
import mars.messages.ReadyPsResponse;
import mars.messages.RestoringPayload;
import mars.messages.UnexpectedPsStateException;
import mars.tower.NoAvailabilityException;
import mars.tower.PitStop;
import mars.tower.PlatformStatus;
import mars.tower.PlatformStatusListener;
import mars.tower.StandardLower;
import mars.tower.TowerMessaging;
import mars.utils.test.TestUtils;

@ExtendWith(MockitoExtension.class)
public class StandardLowerTest {

	private StandardLower out;

	private static final long TIMEOUT_MILLIS = 10_000;

	private static final long SERVICE_MILLIS = 60_000;

	private static final long LANDING_WAIT_MILLIS = 100_000;

	private static final long HALF_MARGIN_MILLIS = 30_000;

	private static final long PREPARE_PS_MILLIS = 45000;

	static {
		TestUtils.loadLog4jStandard();
	}

	@Mock
	private PlatformStatusListener listener;
	@Mock
	private TowerMessaging tm;

	private TestEnv te;

	private final PayloadBay payLoadBayPt1 = new PayloadBay(22,
			new RestoringPayload("Pl100", "PT1", .23, Instant.now().plus(10, MINUTES)), PREPARE_PS_MILLIS);
	private final PayloadBay payLoadBayPt2 = new PayloadBay(23,
			new RestoringPayload("Pl100", "PT2", .43, Instant.now().plus(10, MINUTES)), PREPARE_PS_MILLIS);

	@Mock
	private Consumer<Throwable> onError;

	@BeforeEach
	void setup() {
		te = new TestEnv(tm);
		out = new StandardLower(te.worldModel, te::messagingMaker, te::allConsumer, onError, TIMEOUT_MILLIS,
				LANDING_WAIT_MILLIS, HALF_MARGIN_MILLIS);
		out.addPlatforStatusListener(listener);

	}

	@Test
	void testOnPlatformDiscovered() throws Exception {
		// given
		when(te.tower.sendPlatformStatusRequest("PLT1", new PlatformStatusRequest())).thenReturn(OutgoingInvocation
				.completed(new PlatformStatusResponse(te.plt1Geo, new mars.messages.PlatformStatus(null, te.noAlarms(),
						te.bays(payLoadBayPt1), SERVICE_MILLIS))));
		// when
		te.runEventAndWait(0, () -> te.all.agentDiscovered("PLT1", "com.mars.platform"));

		// then
		final var expected = new ArrayList<PlatformStatus>();
		expected.add(te.platform1With(payLoadBayPt1));
		assertIterableEquals(expected, out.getPlatformsStatus());

		verify(listener).platformStatusUpdate(te.platform1With(OnlineStatus.OFFLINE), te.platform1With(payLoadBayPt1));
	}

	@Test
	void testPlatformStatusIndicationUpdatesBays() throws Exception {
		// given
		te.setPlatformStatus(te.platform1With(te.bay(4, "PT1"), te.bay(8, "PT4"), te.emptyBay(12)));

		final var callCtx = new MockCallContext<>("PLT1",
				new PlatformStatusIndication(new mars.messages.PlatformStatus(null, te.noAlarms(),
						te.bays(te.emptyBay(4), te.bay(12, "PT7")), SERVICE_MILLIS)),
				null);
		// when
		te.runEventAndWait(0, () -> te.pltSent.onPlatformStatus(callCtx));

		// then
		callCtx.checkError();
		te.assertBay("PLT1", 4).isEmpty();
		te.assertBay("PLT1", 8).hasPayload("PT4").isCharged(1);
		te.assertBay("PLT1", 12).hasPayload("PT7").isCharged(1);
	}

	@Test
	void testOnPlatformLostUpdatePlatformStatus() throws Exception {
		// given
		te.setPlatformStatus(te.platform1With());

		// when
		te.runEventAndWait(0, () -> te.all.agentLost("PLT1", "com.mars.platform"));

		// then
		final var expected = new ArrayList<PlatformStatus>();
		expected.add(te.platform1With(OnlineStatus.LOST));
		assertIterableEquals(expected, out.getPlatformsStatus());
		verify(listener).platformStatusUpdate(te.platform1With(), te.platform1With(OnlineStatus.LOST));
	}

	@Test
	void testPlatformLostAbortsPitsStops() throws Exception {
		// given
		te.setPlatformStatus(te.platform1With());
		te.setPitStops(//
				te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> b.state(PsState.REQUESTED)),
				te.ps(4, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)),
						b -> b.state(PsState.IN_TRANSIT_TO_ASSIGNED).assignation("PLT1", 1, te.after(5, MINUTES))));

		psWillBeAborted(4, "MC1", () -> {
			// when
			te.runEventAndWait(0, () -> te.all.agentLost("PLT1", "com.mars.platform"));
		});
	}

	@Test
	void testPlatformReturned() throws Exception {
		// given
		te.setPlatformStatus(te.platform1With(OnlineStatus.LOST));

		when(te.tower.sendPlatformStatusRequest("PLT1", new PlatformStatusRequest())).thenReturn(OutgoingInvocation
				.completed(new PlatformStatusResponse(te.plt1Geo, new mars.messages.PlatformStatus(null, te.noAlarms(),
						te.bays(payLoadBayPt1), SERVICE_MILLIS))));
		// when
		te.runEventAndWait(0, () -> te.all.agentDiscovered("PLT1", "com.mars.platform"));

		// then
		final var expected = new ArrayList<PlatformStatus>();
		expected.add(te.platform1With(payLoadBayPt1));
		assertIterableEquals(expected, out.getPlatformsStatus());

		verify(listener).platformStatusUpdate(te.platform1With(OnlineStatus.LOST), te.platform1With(payLoadBayPt1));
	}

	@ParameterizedTest
	@EnumSource(value = PsState.class, mode = EnumSource.Mode.INCLUDE, names = { "ASSIGNED",
			"IN_TRANSIT_TO_ASSIGNED", })
	void testOnPlatformIndicatesBayEmptyBeforePitstopStartsAbortIt(PsState state) throws Exception {
		// given
		te.setPlatformStatus(te.platform1With(te.bay(1, "PT1")));
		te.setPitStops(te.ps(4, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)),
				b -> b.state(state).assignation("PLT1", 1, te.after(5, MINUTES))));

		psWillBeAborted(4, "MC1", () -> {
			// when
			final var callCtx = new MockCallContext<>("PLT1",
					new PlatformStatusIndication(new mars.messages.PlatformStatus(null, te.noAlarms(),
							te.bays(new PayloadBay(1, null, PREPARE_PS_MILLIS)), SERVICE_MILLIS)),
					null);
			te.runEventAndWait(0, () -> te.pltSent.onPlatformStatus(callCtx));
		});
	}

	@Test
	void testReadyPsRequestIsSentInTime() throws Exception {
		// given
		te.setPlatformStatus(te.platform1With(payLoadBayPt1));
		final PitStop ps = te.ps(4, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)),
				b -> b.state(PsState.READY_TO_SCHEDULE));

		te.setPitStops(ps);
		te.mcWillConfirmAssignment(4, "MC1", "PLT1", 4, MINUTES);
		when(te.tower.sendReadyPsRequest("PLT1", new ReadyPsRequest(4, 22)))
				.thenReturn(OutgoingInvocation.notCompleting());

		te.runEventAndWait(240_000 - HALF_MARGIN_MILLIS - PREPARE_PS_MILLIS, () -> {
			// when
			out.assign(ps, te.assigned("PLT1", 22, 4, MINUTES));
		});

		verify(te.tower).sendReadyPsRequest("PLT1", new ReadyPsRequest(4, 22));
	}

	@Test
	void testReadyPsFailsDuringTravel() throws Exception {
		// given
		te.setPlatformStatus(te.platform1With(payLoadBayPt1));
		final PitStop ps = te.ps(4, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)),
				b -> b.state(PsState.READY_TO_SCHEDULE));

		te.setPitStops(ps);
		te.mcWillConfirmAssignment(4, "MC1", "PLT1", 4, MINUTES);
		when(te.tower.sendReadyPsRequest("PLT1", new ReadyPsRequest(4, 22)))
				.thenReturn(OutgoingInvocation.failed(new CannotReadyPayload("?")));

		psWillBeAborted(4, "MC1", "PLT1", () -> {
			te.runEventAndWait(165_000, () -> {
				// when
				out.assign(ps, te.assigned("PLT1", 22, 4, MINUTES));
			});
		});
	}

	@Test
	void testReadyPsRequestCannotBeSentInTime() throws Exception {
		// given
		te.setPlatformStatus(te.platform1With(payLoadBayPt1));
		final PitStop ps = te.ps(4, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)),
				b -> b.state(PsState.READY_TO_SCHEDULE));

		te.setPitStops(ps);
		te.mcWillConfirmAssignment(4, "MC1", "PLT1", PREPARE_PS_MILLIS - 1, ChronoUnit.MILLIS);

		psWillBeAborted(4, "MC1", () -> {
			te.runEventAndWait(Long.MAX_VALUE, () -> {
				out.assign(ps, te.assigned("PLT1", 22, PREPARE_PS_MILLIS - 1, ChronoUnit.MILLIS));
			});
		});
		verifyNoMoreInteractions(te.tower); // the request must not be sent
	}

	@Test
	void testOnPlatformStatusIndicationUnkonwnBay() throws Exception {
		// given
		te.setPlatformStatus(te.platform1With());

		// when
		final var callCtx = new MockCallContext<>("PLT1",
				new PlatformStatusIndication(
						new mars.messages.PlatformStatus(null, te.noAlarms(), te.bays(payLoadBayPt1), SERVICE_MILLIS)),
				null);
		te.pltSent.onPlatformStatus(callCtx);

		// then
		final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> callCtx.checkError());
		assertEquals("Indication contains one or more unknown bay ids", e.getMessage());
		verifyNoMoreInteractions(listener);
	}

	@Test
	void testOnMcDiscovered() throws Exception {
		// when
		te.runEventAndWait(0, () -> te.all.agentDiscovered("MC1", "com.mars.mc"));

		// then
		Mockito.verifyNoInteractions(listener);
	}

	@Test
	void testOnPsDemandRequest() throws Exception {
		// given
		final Instant endOfDroneLife = Instant.now();

		te.setPlatformStatus(te.platform1With(payLoadBayPt1), te.platform2With(payLoadBayPt1, payLoadBayPt2),
				te.platform3With(payLoadBayPt2));

		listenerCalledWithPitstop(c -> c.hasState(PsState.INIT));

		// when
		final var callCtx = new MockCallContext<>("MC1", new PsDemandRequest(2256, "PT1", endOfDroneLife), null);
		te.runEventAndWait(0, () -> te.mcSent.onPsDemand(callCtx));

		// then
		callCtx.checkError();
		assertEquals(new PsDemandResponse(2256, 1, availabilities(new PsPlatformAvailability("PLT1", te.plt1Geo),
				new PsPlatformAvailability("PLT2", te.plt2Geo))), callCtx.getResult());

		te.assertPitstop(1).hasReqeust(new PsDemandRequest(2256, "PT1", endOfDroneLife)).hasMcId("MC1")
				.hasReachabilites();
	}

	@Test
	void testOnPsDemandRequestNoAvailability() throws Exception {
		te.setPlatformStatus(te.platform1With(), te.platform2With(payLoadBayPt2), te.platform3With(payLoadBayPt2));

		// when
		final var callCtx = new MockCallContext<>("MC1", new PsDemandRequest(2256, "PT1", Instant.now()), null);
		te.runEventAndWait(0, () -> te.mcSent.onPsDemand(callCtx));

		// then
		assertThrows(NoAvailabilityException.class, () -> callCtx.getCheckedResult());
	}

	@Test
	void testOnPlatformReachabilityIndication() throws Exception {
		// given
		te.setPitStops(te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> b.state(PsState.REQUESTED)),
				te.ps(4, "MC2", ZERO, te.request(334, "PT5", of(20, MINUTES)),
						b -> b.state(PsState.IN_TRANSIT_TO_ASSIGNED)));

		listenerCalledWithPitstop(p -> p.hasState(PsState.READY_TO_SCHEDULE));

		// when
		final var callCtx = new MockCallContext<>("MC1",
				new PlatformReachabilityIndication(3, te.withReachabilities(
						te.reachability("PLT1", te.after(3, MINUTES)), te.reachability("PLT2", te.after(5, MINUTES)))),
				null);
		te.runEventAndWait(0, () -> te.mcSent.onPlatformReachability(callCtx));

		// then
		callCtx.checkError();
		assertEquals(new PlatformReachabilityConfirm(), callCtx.getResult());

		te.assertPitstop(4).hasState(PsState.IN_TRANSIT_TO_ASSIGNED).hasReachabilites();
		te.assertPitstop(3)//
				.hasReachabilites(te.reachability("PLT1", te.after(3, MINUTES)),
						te.reachability("PLT2", te.after(5, MINUTES)))//
				.hasState(PsState.READY_TO_SCHEDULE);
	}

	@Test
	void testOnPlatformReachabilityIndicationInTransit() throws Exception {
		// given
		te.setPitStops(te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> b.state(PsState.REQUESTED)),
				te.ps(4, "MC2", ZERO, te.request(334, "PT5", of(20, MINUTES)),
						b -> b.state(PsState.IN_TRANSIT_TO_ASSIGNED)));

		listenerCalledWithPitstop(p -> p.hasState(PsState.IN_TRANSIT_TO_ASSIGNED));

		// when
		final var callCtx = new MockCallContext<>("MC1",
				new PlatformReachabilityIndication(4, te.withReachabilities(
						te.reachability("PLT1", te.after(3, MINUTES)), te.reachability("PLT2", te.after(5, MINUTES)))),
				null);
		te.runEventAndWait(0, () -> te.mcSent.onPlatformReachability(callCtx));

		// then
		callCtx.checkError();
		assertEquals(new PlatformReachabilityConfirm(), callCtx.getResult());

		te.assertPitstop(3).hasState(PsState.REQUESTED).hasReachabilites();
		te.assertPitstop(4)//
				.hasReachabilites(te.reachability("PLT1", te.after(3, MINUTES)),
						te.reachability("PLT2", te.after(5, MINUTES)))//
				.hasState(PsState.IN_TRANSIT_TO_ASSIGNED);
	}

	@Test
	void testOnPlatformReachabilityIndicationEmpty() throws Exception {
		// given
		final PitStop ps = te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)),
				b -> b.state(PsState.REQUESTED));
		te.setPitStops(ps);

		psWillBeAborted(3, "MC1", () -> {

			// when
			final var callCtx = new MockCallContext<>("MC1",
					new PlatformReachabilityIndication(3, te.withReachabilities()), null);
			te.runEventAndWait(0, () -> te.mcSent.onPlatformReachability(callCtx));

			// then
			callCtx.checkError();
			assertEquals(new PlatformReachabilityConfirm(), callCtx.getResult());
			Mockito.verify(listener).psStatusUpdate(ps);
			verifyNoMoreInteractions(listener);
		});
	}

	@Test
	void testOnPlatformAssignmentConfirm() throws Exception {
		// given
		te.setPlatformStatus(te.platform1With(te.bay(12, "PT1")));
		final PitStop ps = te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)),
				b -> b.state(PsState.READY_TO_SCHEDULE));
		te.setPitStops(ps);
		te.mcWillConfirmAssignment(3, "MC1", "PLT1", 2, MINUTES);

		// when
		te.runEventAndWait(0, () -> out.assign(ps, te.assigned("PLT1", 12, 2, MINUTES)));

		// then
		te.assertPitstop(3).hasState(PsState.IN_TRANSIT_TO_ASSIGNED);
	}

	@Test
	void testOnPlatformCylinderEnterRequest() throws Exception {
		// given
		te.setPlatformStatus(te.platform1With(te.bay(12, "PT1")));
		final PitStop ps = te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)),
				b -> b.state(PsState.IN_TRANSIT_TO_ASSIGNED).assignation("PLT1", 12, te.after(2, MINUTES)));
		te.setPitStops(ps);

		when(te.tower.sendReadyPsRequest("PLT1", new ReadyPsRequest(3, 12)))
				.thenReturn(OutgoingInvocation.completed(new ReadyPsResponse()));

		// when
		final var callCtx = new MockCallContext<>("MC1", new PlatformCylinderEnterRequest(3, "PLT1"), null);
		final var callCtx2 = new MockCallContext<>("PLT1",
				new PlatformStatusIndication(new mars.messages.PlatformStatus(3, te.noAlarms(), null, SERVICE_MILLIS)),
				null);

//		final var callCtx = new MockCallContext<>("PLT1",
//				new PlatformStatusIndication(
//						new mars.messages.PlatformStatus(null, new PlatformAlarms(false), te.bays(payLoadBayPt1))),
//				null);

		te.runEventAndWait(120_000, () -> {
			out.prepareOnPlatform(ps);
			El.setTimeout(110_000, () -> te.pltSent.onPlatformStatus(callCtx2));
			El.setTimeout(120_000, () -> te.mcSent.onPlatformCylinderEnter(callCtx));
		});

		// then
		callCtx.checkError();
		callCtx2.checkError();
		assertEquals(new PlatformCylinderEnterResponse(), callCtx.getResult());

		te.assertPitstop(3).hasState(PsState.LANDING_AUTHORIZED);
	}

	@Test
	void testOnPlatformCylinderEnterRequestPreparedAfterArrival() throws Exception {
		// given
		te.setPlatformStatus(te.platform1With(te.bay(12, "PT1")));
		final PitStop ps = te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)),
				b -> b.state(PsState.IN_TRANSIT_TO_ASSIGNED).assignation("PLT1", 12, te.after(2, MINUTES)));
		te.setPitStops(ps);

		when(te.tower.sendReadyPsRequest("PLT1", new ReadyPsRequest(3, 12)))
				.thenReturn(OutgoingInvocation.completed(new ReadyPsResponse()));

		// when
		final var callCtx = new MockCallContext<>("MC1", new PlatformCylinderEnterRequest(3, "PLT1"), null);
		final var callCtx2 = new MockCallContext<>("PLT1",
				new PlatformStatusIndication(new mars.messages.PlatformStatus(3, te.noAlarms(), null, SERVICE_MILLIS)),
				null);

		te.runEventAndWait(155_000, () -> {
			El.setTimeout(110_000, () -> out.prepareOnPlatform(ps));
			El.setTimeout(120_000, () -> te.mcSent.onPlatformCylinderEnter(callCtx));
			El.setTimeout(121_000, () -> te.pltSent.onPlatformStatus(callCtx2));
		});

		// then
		callCtx.checkError();
		assertEquals(new PlatformCylinderEnterResponse(), callCtx.getResult());

		te.assertPitstop(3).hasState(PsState.LANDING_AUTHORIZED);
	}

	@Test
	void testOnPlatformCylinderEnterRequestPreapredArrivesTooLate() throws Exception {
		// given
		te.setPlatformStatus(te.platform1With(te.bay(12, "PT1")));
		final PitStop ps = te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)),
				b -> b.state(PsState.IN_TRANSIT_TO_ASSIGNED).assignation("PLT1", 12, te.after(2, MINUTES)));
		te.setPitStops(ps);

		when(te.tower.sendReadyPsRequest("PLT1", new ReadyPsRequest(3, 12)))
				.thenReturn(OutgoingInvocation.completed(new ReadyPsResponse()));

		psWillBeAborted(3, "PLT1", "MC1", () -> {
			// when
			final var callCtx = new MockCallContext<>("MC1", new PlatformCylinderEnterRequest(3, "PLT1"), null);
			final var callCtx2 = new MockCallContext<>("PLT1", new PlatformStatusIndication(
					new mars.messages.PlatformStatus(3, te.noAlarms(), null, SERVICE_MILLIS)), null);
			te.runEventAndWait(155_000, () -> {
				El.setTimeout(110_000, () -> out.prepareOnPlatform(ps));
				El.setTimeout(120_000, () -> te.mcSent.onPlatformCylinderEnter(callCtx));
				El.setTimeout(155_000, () -> te.pltSent.onPlatformStatus(callCtx2));
			});

			// then
			final var e = assertThrows(TimeoutException.class, () -> callCtx.checkError());
			assertEquals("Platform is not ready", e.getMessage());
		});
	}

	@Test
	void testOnPlatformCylinderEnterRequestWait() throws Exception {
		// given
		te.setPlatformStatus(te.platform1With(te.bay(12, "PT1")));
		final PitStop ps = te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)),
				b -> b.engaged().state(PsState.PLATFORM_ENGAGED).assignation("PLT1", 12, te.after(2, MINUTES)));
		te.setPitStops(ps);

		// when
		final var callCtx = new MockCallContext<>("MC1", new PlatformCylinderEnterRequest(3, "PLT1"), null);
		te.runEventAndWait(124_000, () -> {
			El.setTimeout(120_000, () -> te.mcSent.onPlatformCylinderEnter(callCtx));
		});

		// then
		callCtx.checkError();

		te.assertPitstop(3).hasState(PsState.WAITING_LANDING_PERMISSION);
	}

	@Test
	void testOnReadyPsRequestFails() throws Exception {
		// given
		te.setPlatformStatus(te.platform1With(te.bay(12, "PT1")));
		final PitStop ps = te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)),
				b -> b.state(PsState.IN_TRANSIT_TO_ASSIGNED).assignation("PLT1", 12, te.after(2, MINUTES)));
		te.setPitStops(ps);

		when(te.tower.sendReadyPsRequest("PLT1", new ReadyPsRequest(3, 12)))
				.thenReturn(OutgoingInvocation.failed(new CannotReadyPayload("failure reason")));

		psWillBeAborted(3, "MC1", "PLT1", () -> {

			// when
			te.runEventAndWait(155_000, () -> {
				El.setTimeout(100_000, () -> out.prepareOnPlatform(ps));
			});

			// then
		});
	}

	@Test
	void testOnPlatformCylinderEnterPltNotReady() throws Exception {
		// given
		te.setPlatformStatus(te.platform1With(te.bay(12, "PT1")));
		te.setPitStops(te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)),
				b -> b.state(PsState.PLATFORM_ENGAGED).engaged().assignation("PLT1", 12, te.after(2, MINUTES))));

		destIsConfirmingAbort("PLT1", 3);
		psWillBeAborted(3, "MC1", () -> {
			// when
			final var callCtx = new MockCallContext<>("MC1", new PlatformCylinderEnterRequest(3, "PLT1"), null);
			te.runEventAndWait(240_000, () -> El.setTimeout(120_000, () -> te.mcSent.onPlatformCylinderEnter(callCtx)));

			// then
			final var e = assertThrows(TimeoutException.class, () -> callCtx.checkError());
			assertEquals("Platform is not ready", e.getMessage());
		});
	}

	@Test
	void testOnPlatformCylinderEnterPltNotYetReady() throws Exception {
		// given
		te.setPlatformStatus(te.platform1With(te.bay(12, "PT1")));
		final PitStop ps = te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)),
				b -> b.state(PsState.IN_TRANSIT_TO_ASSIGNED).assignation("PLT1", 12, te.after(2, MINUTES)));
		te.setPitStops(ps);

		when(te.tower.sendReadyPsRequest("PLT1", new ReadyPsRequest(3, 12)))
				.thenReturn(OutgoingInvocation.completed(new ReadyPsResponse()));

		psWillBeAborted(3, "MC1", "PLT1", () -> {

			// when
			final var callCtx = new MockCallContext<>("MC1", new PlatformCylinderEnterRequest(3, "PLT1"), null);
			te.runEventAndWait(Long.MAX_VALUE, () -> {
				out.prepareOnPlatform(ps);
				El.setTimeout(120_000, () -> te.mcSent.onPlatformCylinderEnter(callCtx));
			});

			// then
			final var e = assertThrows(TimeoutException.class, () -> callCtx.checkError());
			assertEquals("Platform is not ready", e.getMessage());
		});
	}

	@Test
	void testOnPlatformCylinderEnterRequestBadPsState() throws Exception {
		// given
		te.setPitStops(te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)),
				b -> b.assignation("PLT1", 1, te.after(2, MINUTES)).engaged().state(PsState.IN_PROGRESS)));

		platformWillBeIndicatedWithCylinderBusy("PLT1");

		psWillBeAborted(3, "PLT1", "MC1", () -> {

			// when
			final var callCtx = new MockCallContext<>("MC1", new PlatformCylinderEnterRequest(3, "PLT1"), null);
			te.runEventAndWait(0, () -> te.mcSent.onPlatformCylinderEnter(callCtx));

			// then
			final var e = assertThrows(UnexpectedPsStateException.class, () -> callCtx.checkError());
			assertEquals("Expecting one of these states: [PLATFORM_ENGAGED] but was: IN_PROGRESS", e.getMessage());
		});
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, -1 })
	void testOnPlatformCylinderEnterRequestWrongArrival(int minutesOffset) throws Exception {
		// given
		te.setPitStops(te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)),
				b -> b.engaged().state(PsState.PLATFORM_ENGAGED).assignation("PLT1", 12, te.after(2, MINUTES))));
		final long arrivalTimeMillis = (2 - minutesOffset) * 60 * 1000; // 1 MINUTE EARLY / LATE

		destIsConfirmingAbort("PLT1", 3);
		psWillBeAborted(3, "MC1", () -> {

			// when
			final var callCtx = new MockCallContext<>("MC1", new PlatformCylinderEnterRequest(3, "PLT1"), null);
			te.runEventAndWait(Long.MAX_VALUE,
					() -> El.setTimeout(arrivalTimeMillis, () -> te.mcSent.onPlatformCylinderEnter(callCtx)));

			// then
			final var e = assertThrows(RuntimeException.class, () -> callCtx.checkError());
			assertEquals("PS not in time on arrival", e.getMessage());
		});
	}

	@Test
	void testOnDroneLandedIndication() throws Exception {
		// given
		te.setPitStops(te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> {
			b.state(PsState.LANDING_AUTHORIZED);
			b.assignation("PLT1", 1, te.t0);
		}));

		when(te.tower.sendDroneLandedIndication("PLT1", new DroneLandedIndication(3, "PLT1", "drone xyz")))
				.thenReturn(OutgoingInvocation.completed(new DroneLandedConfirm()));

		// when
		final var callCtx = new MockCallContext<>("MC1", new DroneLandedIndication(3, "PLT1", "drone xyz"), null);
		te.runEventAndWait(0, () -> te.mcSent.onDroneLanded(callCtx));

		// then
		callCtx.checkError();
		assertEquals(new DroneLandedConfirm(), callCtx.getResult());

		te.assertPitstop(3).hasState(PsState.IN_PROGRESS);
	}

	@Test
	void testOnDroneLandedIndicationTimeout() throws Exception {
		// given
		te.setPlatformStatus(te.platform1With(te.bay(12, "PT1")));
		te.setPitStops(te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> {
			b.engaged().state(PsState.PLATFORM_ENGAGED);
			b.assignation("PLT1", 1, te.t0);
		}));
		final long landingWaitExtraMillis = LANDING_WAIT_MILLIS + 1000; // 1 second delay
		destIsConfirmingAbort("MC1", 3);
		destIsConfirmingAbort("PLT1", 3);

		// when
		final var callCtx1 = new MockCallContext<>("MC1", new PlatformCylinderEnterRequest(3, "PLT1"), null);
		final var callCtx2 = new MockCallContext<>("MC1", new DroneLandedIndication(3, "PLT1", "DRONE X"), null);
		final var callCtx3 = new MockCallContext<>("PLT1",
				new PlatformStatusIndication(new mars.messages.PlatformStatus(3, null, null, SERVICE_MILLIS)), null);

		te.runEventAndWait(Long.MAX_VALUE, () -> {
			te.pltSent.onPlatformStatus(callCtx3);
			te.mcSent.onPlatformCylinderEnter(callCtx1);
			El.setTimeout(landingWaitExtraMillis, () -> te.mcSent.onDroneLanded(callCtx2));
		});

		// then
		callCtx1.checkError();
		final var e = assertThrows(UnexpectedPsStateException.class, () -> callCtx2.checkError());
		assertEquals("Expecting none of these states: [FAILED_PS, CLEARING_CYLINDER] but was: CLEARING_CYLINDER",
				e.getMessage());
		te.assertPitstop(3).hasState(PsState.CLEARING_CYLINDER);
	}

	@Test
	void testOnPsCompletedIndication() throws Exception {
		// given
		te.setPlatformStatus(te.platform1With(te.bay(12, "PT1")));
		final PitStop ps = te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> {
			b.state(PsState.IN_PROGRESS);
			b.assignation("PLT1", 12, te.t0);
		});
		te.setPitStops(ps);
		when(te.tower.sendPsCompletedIndication("MC1", new PsCompletedIndication(3, "PLT1")))
				.thenReturn(OutgoingInvocation.completed(new PsCompletedConfirm()));

		// when
		final var callCtx = new MockCallContext<>("PLT1", new PsCompletedIndication(3, "PLT1"), null);
		te.runEventAndWait(0, () -> {
			te.pltSent.onPsCompleted(callCtx);
		});

		// then
		callCtx.checkError();
		assertEquals(new PsCompletedConfirm(), callCtx.getResult());

		te.assertPitstop(3).hasState(PsState.LIFT_OFF);
	}

	@Test
	void testOnPsCompletedIndicationTimeoutMc() throws Exception {
		// given
		te.setPlatformStatus(te.platform1With(te.bay(12, "PT1")));
		te.setPitStops(te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> {
			b.state(PsState.IN_PROGRESS);
			b.assignation("PLT1", 12, te.t0);
		}));
		when(te.tower.sendPsCompletedIndication("MC1", new PsCompletedIndication(3, "PLT1")))
				.thenReturn(OutgoingInvocation.notCompleting());

		// when
		final var callCtx = new MockCallContext<>("PLT1", new PsCompletedIndication(3, "PLT1"), null);
		te.runEventAndWait(TIMEOUT_MILLIS, () -> te.pltSent.onPsCompleted(callCtx));

		// then
		final var e = assertThrows(RuntimeException.class, () -> callCtx.checkError());
		assertEquals("MC timed out", e.getMessage());

		te.assertPitstop(3).hasState(PsState.ABORTED);
	}

	@ParameterizedTest
	@EnumSource(value = PsState.class, names = { "LANDED", "READY_TO_LIFT_OFF", "LIFT_OFF", "FAILED_PS",
			"CLEARING_CYLINDER" })
	void testOnPsCompletedIndicationInvalidStateWithAbortMcAndPltAndCylinderBusy(PsState state) throws Exception {
		// given
		platformWillBeIndicatedWithCylinderBusy("PLT1");
		te.setPitStops(te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> {
			b.engaged().state(state);
			b.assignation("PLT1", 1, te.t0);
		}));

		destIsConfirmingAbort("PLT1", 3);
		psWillBeAborted(3, "MC1", () -> {

			// when
			final var callCtx = new MockCallContext<>("PLT1", new PsCompletedIndication(3, "PLT1"), null);
			te.runEventAndWait(0, () -> te.pltSent.onPsCompleted(callCtx));

			// then
			final var e = assertThrows(UnexpectedPsStateException.class, () -> callCtx.checkError());
			assertEquals("Expecting one of these states: [IN_PROGRESS] but was: " + state, e.getMessage());
		});
	}

	@ParameterizedTest
	@EnumSource(value = PsState.class, names = { "PLATFORM_ENGAGED", "WAITING_LANDING_PERMISSION",
			"LANDING_AUTHORIZED", })
	void testOnPsCompletedIndicationInvalidStateWithAbortMcAndPlt(PsState state) throws Exception {
		// given
		te.setPitStops(te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> {
			b.engaged().state(state);
			b.assignation("PLT1", 1, te.t0);
		}));

		destIsConfirmingAbort("PLT1", 3);
		psWillBeAborted(3, "MC1", () -> {

			// when
			final var callCtx = new MockCallContext<>("PLT1", new PsCompletedIndication(3, "PLT1"), null);
			te.runEventAndWait(0, () -> te.pltSent.onPsCompleted(callCtx));

			// then
			final var e = assertThrows(UnexpectedPsStateException.class, () -> callCtx.checkError());
			assertEquals("Expecting one of these states: [IN_PROGRESS] but was: " + state, e.getMessage());
		});
	}

	@ParameterizedTest
	@EnumSource(value = PsState.class, names = { "REQUESTED", "READY_TO_SCHEDULE", "ASSIGNED", "IN_TRANSIT_TO_ASSIGNED",
			"IN_TRANSIT_TO_OLD_ASSIGNED" })
	void testOnPsCompletedIndicationInvalidStateWithAbortOnlyMc(PsState state) throws Exception {
		// given
		te.setPitStops(te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> {
			b.state(state);
			b.assignation("PLT1", 1, te.t0);
		}));

		psWillBeAborted(3, "MC1", () -> {

			// when
			final var callCtx = new MockCallContext<>("PLT1", new PsCompletedIndication(3, "PLT1"), null);
			te.runEventAndWait(0, () -> te.pltSent.onPsCompleted(callCtx));

			// then
			final var e = assertThrows(UnexpectedPsStateException.class, () -> callCtx.checkError());
			assertEquals("Expecting one of these states: [IN_PROGRESS] but was: " + state, e.getMessage());
		});
	}

	@ParameterizedTest
	@EnumSource(value = PsState.class, mode = EnumSource.Mode.INCLUDE, names = { "ABORTING", "ABORTED", "ENDED" })
	void testOnPsCompletedIndicationInvalidState(PsState state) throws Exception {
		// given
		te.setPitStops(te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> {
			b.state(state);
			b.assignation("PLT1", 1, te.t0);
		}));

		// when
		final var callCtx = new MockCallContext<>("PLT1", new PsCompletedIndication(3, "PLT1"), null);
		te.runEventAndWait(0, () -> te.pltSent.onPsCompleted(callCtx));

		// then
		final var e = assertThrows(UnexpectedPsStateException.class, () -> callCtx.checkError());
		assertEquals("Expecting one of these states: [IN_PROGRESS] but was: " + state, e.getMessage());
	}

	@Test
	void testOnPlatformCylinderLeftIndication() throws Exception {
		// given
		te.setPitStops(te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> {
			b.state(PsState.LIFT_OFF);
			b.assignation("PLT1", 1, te.t0);
		}));

		// when
		final var callCtx = new MockCallContext<>("PLT1", new PlatformCylinderLeftIndication(3, "PLT1"), null);
		te.runEventAndWait(0, () -> te.mcSent.onPlatformCylinderLeft((callCtx)));

		// then
		callCtx.checkError();
		assertEquals(new PlatformCylinderLeftConfirm(), callCtx.getResult());

		te.assertPitstop(3).hasState(PsState.ENDED);
	}

	@Test
	void testOnPlatformCylinderLeftIndicationFromFailedPs() throws Exception {
		// given
		te.setPitStops(te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> {
			b.state(PsState.CLEARING_CYLINDER);
			b.assignation("PLT1", 1, te.t0);
		}));

		// when
		final var callCtx = new MockCallContext<>("PLT1", new PlatformCylinderLeftIndication(3, "PLT1"), null);
		te.runEventAndWait(0, () -> te.mcSent.onPlatformCylinderLeft((callCtx)));

		// then
		callCtx.checkError();
		assertEquals(new PlatformCylinderLeftConfirm(), callCtx.getResult());

		te.assertPitstop(3).hasState(PsState.ABORTED);
	}

	@Test
	void testOnPlatformCylinderLeftIndicationInvalidState() throws Exception {
		// given
		te.setPitStops(te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> {
			b.state(PsState.ASSIGNED);
			b.assignation("PLT1", 1, te.t0);
		}));
		destIsConfirmingAbort("MC1", 3);

		// when
		final var callCtx = new MockCallContext<>("PLT1", new PlatformCylinderLeftIndication(3, "PLT1"), null);
		te.runEventAndWait(0, () -> te.mcSent.onPlatformCylinderLeft((callCtx)));

		// then
		callCtx.checkError();
		assertEquals(new PlatformCylinderLeftConfirm(), callCtx.getResult());

		te.assertPitstop(3).hasState(PsState.ABORTED);
	}

	@ParameterizedTest
	@EnumSource(value = PsState.class, mode = EnumSource.Mode.INCLUDE, names = { "ASSIGNED", "REQUESTED",
			"IN_TRANSIT_TO_ASSIGNED", "IN_TRANSIT_TO_OLD_ASSIGNED", "WAITING_LANDING_PERMISSION", "READY_TO_SCHEDULE" })
	void testOnPsAbortIndicationValidState(PsState state) throws Exception {
		// given
		te.setPitStops(te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> {
			b.state(state);
			b.assignation("PLT1", 1, te.t0);
		}));

		// when
		final var callCtx = new MockCallContext<>("MC1", new PsAbortIndication(3), null);
		te.runEventAndWait(0, () -> te.mcSent.onPsAbort((callCtx)));

		// then
		callCtx.checkError();
		assertEquals(new PsAbortConfirm(), callCtx.getResult());

		te.assertPitstop(3).hasState(PsState.ABORTED);
	}

	@ParameterizedTest
	@EnumSource(value = PsState.class, mode = EnumSource.Mode.EXCLUDE, names = { "ASSIGNED", "REQUESTED",
			"IN_TRANSIT_TO_ASSIGNED", "IN_TRANSIT_TO_OLD_ASSIGNED", "WAITING_LANDING_PERMISSION", "READY_TO_SCHEDULE" })
	void testOnPsAbortIndicationInvalidState(PsState state) throws Exception {
		// given
		te.setPitStops(te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> {
			b.state(state);
			b.assignation("PLT1", 1, te.t0);
		}));

		// when
		final var callCtx = new MockCallContext<>("MC1", new PsAbortIndication(3), null);
		te.runEventAndWait(0, () -> te.mcSent.onPsAbort((callCtx)));

		// then
		final var e = assertThrows(UnexpectedPsStateException.class, () -> callCtx.checkError());
		assertEquals(
				"Expecting one of these states: [REQUESTED, READY_TO_SCHEDULE, ASSIGNED, IN_TRANSIT_TO_ASSIGNED, IN_TRANSIT_TO_OLD_ASSIGNED, WAITING_LANDING_PERMISSION] but was: "
						+ state,
				e.getMessage());

		te.assertPitstop(3).hasState(state);
	}

	@Test
	void testOnPsAbortConfirmFromMcOnFailedPs() throws Exception {
		// given
		final PitStop ps = te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> {
			b.state(PsState.FAILED_PS);
			b.assignation("PLT1", 1, te.t0);
		});
		te.setPitStops(ps);
		destIsConfirmingAbort("MC1", 3);

		// when
		te.runEventAndWait(0, () -> out.sendPsAbortToMc("MC1", new PsAbortIndication(3), ps));

		// then
		te.assertPitstop(3).hasState(PsState.CLEARING_CYLINDER);
	}

	@Test
	void testAbortPs() throws Exception {
		final int psId = 3;
		// given
		final PitStop ps = te.ps(psId, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> {
			b.state(PsState.READY_TO_SCHEDULE);
		});
		te.setPitStops(ps);
		destIsConfirmingAbort("MC1", psId);

		// when
		te.runEventAndWait(1, () -> out.abortPs(ps));

		// then
		te.assertPitstop(psId).hasState(PsState.ABORTED);
	}

	@ParameterizedTest
	@EnumSource(value = PsState.class, mode = EnumSource.Mode.INCLUDE, names = { "ABORTED", "ABORTING", "ENDED" })
	void testAbortingPsDoesNothing(PsState state) throws Exception {
		// given
		final PitStop ps = te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> {
			b.state(state);
		});

		// when
		out.abortPs(ps);

		// then
		Mockito.verifyNoInteractions(te.tower);
	}

	@Test
	void testAbortPsMoveItIntoGraveyard() throws Exception {
		// given
		final PitStop ps = te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> {
			b.state(PsState.READY_TO_SCHEDULE);
		});
		te.setPitStops(ps);
		destIsConfirmingAbort("MC1", 3);

		// when
		te.runEventAndWait(1, () -> out.abortPs(ps));

		// then
		assertFalse(te.worldModel.getPitStops().contains(ps));
		assertTrue(te.worldModel.getBuriedPitStops().contains(ps));
	}

	@Test
	void testCompletePsMoveItIntoGraveyardNotBeforePlatformOccupation() throws Exception {
		// given
		final PitStop ps = te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> {
			b.assignation("PLT1", 3, te.after(3, MINUTES), te.after(2, MINUTES), te.after(6, MINUTES)).engaged()
					.state(PsState.LIFT_OFF);
		});
		te.setPitStops(ps);
		final int expectedBurialTime = 6 * 60_000;

		// when
		final var callCtx = new MockCallContext<>("MC1", new PlatformCylinderLeftIndication(3, "PLT1"), null);
		te.runEventAndWait(expectedBurialTime, () -> {
			te.mcSent.onPlatformCylinderLeft(callCtx);
			El.setTimeout(expectedBurialTime - 1, () -> {
				assertTrue(te.worldModel.getPitStops().contains(ps));
				assertFalse(te.worldModel.getBuriedPitStops().contains(ps));
			});
		});

		// then
		assertFalse(te.worldModel.getPitStops().contains(ps));
		assertTrue(te.worldModel.getBuriedPitStops().contains(ps));
	}

	@Test
	void testCompletePsMoveItIntoGraveyardWithAssignation() throws Exception {
		// given
		final PitStop ps = te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> {
			b.state(PsState.LIFT_OFF).assignation("PL1", 12, te.t0);
		});
		te.setPitStops(ps);

		// when
		final var callCtx = new MockCallContext<>("MC1", new PlatformCylinderLeftIndication(3, "PLT1"), null);
		// I have to wait the schedule for bury pitstop
		te.runEventAndWait(300_000, () -> te.mcSent.onPlatformCylinderLeft(callCtx));

		// then
		assertFalse(te.worldModel.getPitStops().contains(ps));
		assertTrue(te.worldModel.getBuriedPitStops().contains(ps));
	}

	@Test
	void testAssignNewPs() throws Exception {
		// given
		te.setPlatformStatus(te.platform1With(te.bay(3, "PT1")));
		final PitStop ps = te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> {
			b.state(PsState.READY_TO_SCHEDULE);
		});
		te.setPitStops(ps);
		te.mcWillConfirmAssignment(3, "MC1", "PLT1", 3, MINUTES);

		// when
		te.runEventAndWait(1, () -> out.assign(ps, te.assigned("PLT1", 3, 3, MINUTES)));

		// then
		te.assertPitstop(3).hasState(PsState.IN_TRANSIT_TO_ASSIGNED);
	}

	@Test
	void testAssignNewPsWrongState() throws Exception {
		// given
		final PitStop ps = te.ps(3, "MC1", ZERO, te.request(333, "PT1", of(20, MINUTES)), b -> {
			b.assignation("PLT1", 3, te.after(2, MINUTES)).engaged().state(PsState.WAITING_LANDING_PERMISSION);
		});
		te.setPitStops(ps);

		// when
		te.runEventAndWait(1, () -> out.assign(ps, te.assigned("PLT1", 3, 3, MINUTES)));

		// then
		verifyNoMoreInteractions(te.tower); // no indication sent to MC
		verify(onError).accept(exception(new UnexpectedPsStateException(
				"Expecting one of these states: [READY_TO_SCHEDULE, ASSIGNED, IN_TRANSIT_TO_ASSIGNED, IN_TRANSIT_TO_OLD_ASSIGNED] but was: WAITING_LANDING_PERMISSION")));
	}

	private void psWillBeAborted(int psId, String mcId, ExceptingRunnable r) throws Exception {
		psWillBeAborted(psId, mcId, null, r);
	}

	private void psWillBeAborted(int psId, String mcId, String pltId, ExceptingRunnable r) throws Exception {
		if (pltId != null)
			destIsConfirmingAbort(pltId, psId);
		destIsConfirmingAbort(mcId, psId);
		r.run();
		te.assertPitstop(psId).hasState(PsState.ABORTED);
	}

	private List<PsPlatformAvailability> availabilities(PsPlatformAvailability... psPlatformAvailability) {
		return Arrays.asList(psPlatformAvailability);
	}

	private void listenerCalledWithPitstop(final Consumer<PitstopChecker> c) {
		Mockito.doAnswer(i -> {
			final PitStop ps = i.getArgument(0);
			c.accept(te.assertPitstop(ps));
			return null;
		}).when(listener).psStatusUpdate(Mockito.any());
	}

	private void destIsConfirmingAbort(String destId, int psId) {
		when(te.tower.sendPsAbortIndication(destId, new PsAbortIndication(psId)))
				.thenReturn(OutgoingInvocation.completed(new PsAbortConfirm()));
	}

	private Throwable exception(Throwable expectedException) {
		return argThat(new ArgumentMatcher<Throwable>() {

			@Override
			public boolean matches(Throwable actual) {
				return actual.toString().equals(expectedException.toString());
			}

			@Override
			public String toString() {
				return "is not " + expectedException;
			}
		});
	}

	private void platformWillBeIndicatedWithCylinderBusy(String destAgentName) {
		when(te.tower.sendAnomalyIndication(destAgentName, new AnomalyIndication(EnumSet.of(Anomaly.CYLINDER_BUSY))))
				.thenReturn(OutgoingInvocation.completed(new AnomalyConfirm()));
	}
}
