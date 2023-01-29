package mars.platform.logics.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cellply.invosys.agent.MockCallContext;
import com.cellply.invosys.agent.OutgoingInvocation;

import eventloop.El;
import mars.messages.CannotStartPsException;
import mars.messages.DroneLandedIndication;
import mars.messages.GeoCoord;
import mars.messages.PayloadBay;
import mars.messages.PlatformAlarms;
import mars.messages.PlatformQuitRequest;
import mars.messages.PlatformQuitResponse;
import mars.messages.PlatformStatus;
import mars.messages.PlatformStatusConfirm;
import mars.messages.PlatformStatusIndication;
import mars.messages.PlatformStatusRequest;
import mars.messages.PlatformStatusResponse;
import mars.messages.PsAbortIndication;
import mars.messages.PsCompletedConfirm;
import mars.messages.PsCompletedIndication;
import mars.messages.RestoringPayload;
import mars.messages.UnexpectedDroneExcpetion;
import mars.platform.logics.Payload;
import mars.platform.logics.PlatformAutomationSystem;
import mars.platform.logics.PlatformEventEmitter;
import mars.platform.logics.PlatformLogics;
import mars.platform.sim.SimulativeBay;
import mars.platform.sim.dummy.DummyPlatformInternals;
import mars.platform.sim.test.TestEnv;
import mars.utils.test.TestUtils;

@ExtendWith(MockitoExtension.class)
class PlatformLogicsTest {

	private static final int SVC_MARGIN_MILLIS = 10_000;

	private static final long SVC_MILLIS = 90_000L;

	private static final int COMMS_TIMEOUT = 1000;

	static {
		TestUtils.loadLog4jStandard();
	}

	private final TestEnv testEnv = new TestEnv();

	private PlatformLogics<SimulativeBay> out;

	private final GeoCoord geoCoord1 = new GeoCoord(8, 44);
	final Payload payload1 = new Payload("PT1", "PL1");

	private DummyPlatformInternals internals;

	@Mock
	private PlatformAutomationSystem<SimulativeBay> automationSystem;

	@Mock
	private PlatformEventEmitter eventEmitter;

	private final String pltName = "plt name";

	@BeforeEach
	void setup() {
		internals = new DummyPlatformInternals(automationSystem);
		out = new PlatformLogics<SimulativeBay>(testEnv::messagingMaker, testEnv::allConsumer, eventEmitter, 1000,
				SVC_MARGIN_MILLIS, pltName);
		internals.setGeoCoord(geoCoord1);
	}

	@Test
	void testStatusRequest() throws Exception {
		// given
		payload1.charge = .65;
		when(automationSystem.getServiceTime()).thenReturn(SVC_MILLIS);
		initWith(bayWith(Optional.of(payload1), .001));

		// when
		final var callCtx = new MockCallContext<>("TOWER", new PlatformStatusRequest(), null);
		testEnv.runEventAndWait(1000, () -> testEnv.tmr.onPlatformStatus(callCtx));

		// then
		final PlatformStatusResponse expected = new PlatformStatusResponse(geoCoord1,
				new PlatformStatus(null, noAlarms(),
						withBays(bay(1, withPayload("PL1", "PT1", .65, testEnv.at(349_999, ChronoUnit.MILLIS)))),
						SVC_MILLIS));
		assertEquals(expected, callCtx.getCheckedResult());
	}

	private PlatformAlarms noAlarms() {
		return new PlatformAlarms(false, false);
	}

	@Test
	void onDroneLandedIndication() throws Exception {
		// given
		initWith();
		when(automationSystem.getServiceTime()).thenReturn(SVC_MILLIS);
		when(automationSystem.getLandedDroneId()).thenReturn("drone XYZ");
		pitStopIsServedIn(60_000);
		when(testEnv.pm.sendPsCompletedIndication(new PsCompletedIndication(34, pltName)))
				.thenReturn(OutgoingInvocation.completed(new PsCompletedConfirm()));
		when(testEnv.pm.sendPlatformStatusIndication(
				new PlatformStatusIndication(new PlatformStatus(null, noAlarms(), null, SVC_MILLIS))))
						.thenReturn(OutgoingInvocation.completed(new PlatformStatusConfirm()));

		// when
		final var callCtx = new MockCallContext<>("TOWER", new DroneLandedIndication(34, "anything", "drone XYZ"),
				null);
		testEnv.runEventAndWait(60_000, () -> testEnv.tmr.onDroneLanded(callCtx));

		// then
		callCtx.checkError();
	}

	@Test
	void onDroneLandedIndicationAutomationTimeout() throws Exception {
		// given
		initWith();
		when(automationSystem.getServiceTime()).thenReturn(SVC_MILLIS);
		when(automationSystem.getLandedDroneId()).thenReturn("drone XYZ");
		pitStopIsServedIn(SVC_MILLIS + SVC_MARGIN_MILLIS + 1);
		when(testEnv.pm.sendPlatformStatusIndication(any()))
				.thenReturn(OutgoingInvocation.completed(new PlatformStatusConfirm()));

		// when
		final var callCtx = new MockCallContext<>("TOWER", new DroneLandedIndication(34, "anything", "drone XYZ"),
				null);
		testEnv.runEventAndWait(SVC_MILLIS + SVC_MARGIN_MILLIS, () -> testEnv.tmr.onDroneLanded(callCtx));

		// then
		verify(testEnv.pm).sendPlatformStatusIndication(
				new PlatformStatusIndication(new PlatformStatus(34, new PlatformAlarms(true, false), any(), 60_000)));
		verifyNoMoreInteractions(automationSystem);
		verifyNoMoreInteractions(testEnv.pm);
		verify(eventEmitter).emitFlangeUnavailable(true);
	}

	@Test
	void resetFlangeUnavailableAlarm() throws Exception {
		// given
		initWith();
		when(testEnv.pm.sendPlatformStatusIndication(any()))
				.thenReturn(OutgoingInvocation.completed(new PlatformStatusConfirm()));
		// when
		testEnv.runEventAndWait(0, () -> out.resetFlangeUnavailableAlarm());

		// then
		verify(testEnv.pm).sendPlatformStatusIndication(
				new PlatformStatusIndication(new PlatformStatus(null, noAlarms(), any(), SVC_MILLIS)));
		verifyNoMoreInteractions(testEnv.pm);
		verify(eventEmitter).emitFlangeUnavailable(false);
	}

	@Test
	void onDroneLandedIndicationConfirmError() throws Exception {
		// given
		initWith();
		when(automationSystem.getServiceTime()).thenReturn(SVC_MILLIS);
		when(automationSystem.getLandedDroneId()).thenReturn("drone XYZ");
		pitStopIsServedIn(60_000);
		when(testEnv.pm.sendPsCompletedIndication(new PsCompletedIndication(34, pltName)))
				.thenReturn(OutgoingInvocation.failed(new Exception()));
		when(testEnv.pm.sendPlatformStatusIndication(
				new PlatformStatusIndication(new PlatformStatus(null, noAlarms(), null, SVC_MILLIS))))
						.thenReturn(OutgoingInvocation.completed(new PlatformStatusConfirm()));

		// when
		final var callCtx = new MockCallContext<>("TOWER", new DroneLandedIndication(34, "anything", "drone XYZ"),
				null);
		testEnv.runEventAndWait(60_000, () -> testEnv.tmr.onDroneLanded(callCtx));

		// then
		callCtx.checkError();
	}

	@Test
	void onDroneLandedIndicationConfirmTimeout() throws Exception {
		// given
		initWith();
		when(automationSystem.getServiceTime()).thenReturn(SVC_MILLIS);
		when(automationSystem.getLandedDroneId()).thenReturn("drone XYZ");

		pitStopIsServedIn(60_000);
		when(testEnv.pm.sendPsCompletedIndication(new PsCompletedIndication(34, pltName)))
				.thenReturn(OutgoingInvocation.notCompleting());

		when(testEnv.pm.sendPlatformStatusIndication(Mockito.any()))
				.thenReturn(OutgoingInvocation.completed(new PlatformStatusConfirm()));
		// when
		final var callCtx = new MockCallContext<>("TOWER", new DroneLandedIndication(34, "anything", "drone XYZ"),
				null);
		testEnv.runEventAndWait(COMMS_TIMEOUT, () -> testEnv.tmr.onDroneLanded(callCtx));

		// then
		verifyNoMoreInteractions(automationSystem);
	}

	@Test
	void onDroneLandedIndicationNotReady() throws Exception {
		// given
		initWith();
		Mockito.doThrow(new CannotStartPsException("failure reason")).when(automationSystem)
				.startPitStop(Mockito.any());
		when(automationSystem.getLandedDroneId()).thenReturn("drone XYZ");

		// when
		final var callCtx = new MockCallContext<>("TOWER", new DroneLandedIndication(34, "anything", "drone XYZ"),
				null);
		testEnv.runEventAndWait(0, () -> testEnv.tmr.onDroneLanded(callCtx));

		// then
		final var e = assertThrows(CannotStartPsException.class, () -> callCtx.checkError());
		assertEquals("failure reason", e.getMessage());
		verifyNoInteractions(testEnv.pm);
	}

	@Test
	void onDroneLandedIndicationBadDrone() throws Exception {
		// given
		initWith();
		when(automationSystem.getLandedDroneId()).thenReturn("drone xyz");

		// when
		final var callCtx = new MockCallContext<>("TOWER", new DroneLandedIndication(34, "anything", "drone XYZ"),
				null);
		testEnv.runEventAndWait(0, () -> testEnv.tmr.onDroneLanded(callCtx));

		// then
		final var e = assertThrows(UnexpectedDroneExcpetion.class, () -> callCtx.checkError());
		assertEquals("I was expecting drone drone XYZ but drone landed is drone xyz", e.getMessage());
		verifyNoInteractions(testEnv.pm);
	}

	@Test
	void onPsAbortIndication() throws Exception {
		initWith();
		// when
		final var callCtx = new MockCallContext<>("TOWER", new PsAbortIndication(37), null);
		testEnv.runEventAndWait(0, () -> testEnv.tmr.onPsAbort(callCtx));

		// then
		callCtx.checkError();
		verify(automationSystem).abortPitStop(Mockito.any());
	}

	@Test
	void onPayloadRechargedSendStatusUpdate() throws Exception {
		// given
		when(automationSystem.getServiceTime()).thenReturn(SVC_MILLIS);
		final var bay1 = bayWith(Optional.empty(), .01);
		initWith(bay1);

		when(testEnv.pm.sendPlatformStatusIndication(new PlatformStatusIndication(new PlatformStatus(null, noAlarms(),
				withBays(bay(1, withPayload("PL1", "PT1", 1d, null))), SVC_MILLIS))))
						.thenReturn(OutgoingInvocation.completed(new PlatformStatusConfirm()));

		// when
		testEnv.runEventAndWait(100_000, () -> bay1.put(payload1));

	}

	private CompletableFuture<?> quitCs;
	private boolean completedTooSoon;

	@Test
	void quitPlatform() throws Exception {
		// given
		when(testEnv.pm.sendPlatformQuitRequest(new PlatformQuitRequest(pltName))).thenReturn(
				OutgoingInvocation.completed(new PlatformQuitResponse(34, testEnv.at(2, ChronoUnit.MINUTES))));

		testEnv.runEventAndWait(120_000, () -> {
			// when
			quitCs = out.quit();
			El.setTimeout(120_000 - 1, () -> {
				completedTooSoon = quitCs.isDone();
			});
		});

		// then
		assertFalse(completedTooSoon);
		assertTrue(quitCs.isDone());
	}

	@Test
	void quitPlatformError() throws Exception {
		// given
		when(testEnv.pm.sendPlatformQuitRequest(new PlatformQuitRequest(pltName)))
				.thenReturn(OutgoingInvocation.failed(new Exception()));

		testEnv.runEventAndWait(0, () -> {
			// when
			quitCs = out.quit();
		});

		// then
		assertTrue(quitCs.isCompletedExceptionally());
	}

	@Test
	void quitPlatformTimeout() throws Exception {
		// given
		when(testEnv.pm.sendPlatformQuitRequest(new PlatformQuitRequest(pltName)))
				.thenReturn(OutgoingInvocation.notCompleting());

		testEnv.runEventAndWait(COMMS_TIMEOUT, () -> {
			// when
			quitCs = out.quit();
		});

		// then
		assertTrue(quitCs.isCompletedExceptionally());
	}

	private void initWith(SimulativeBay... bays) {
		when(automationSystem.getPayloadBays()).thenReturn(Arrays.asList(bays));
		out.setModel(internals);
	}

	private void pitStopIsServedIn(long millis) throws CannotStartPsException {
		Mockito.doAnswer(i -> {
			final Runnable r = i.getArgument(0);
			final CompletableFuture<Void> cf = new CompletableFuture<>();
			El.setTimeout(millis, () -> {
				r.run();
				cf.complete(null);
			});
			return cf;

		}).when(automationSystem).startPitStop(Mockito.any());

	}

	private SimulativeBay bayWith(Optional<Payload> payload, double chargePerSecond) {
		return new SimulativeBay(1, payload, chargePerSecond, 45_000);
	}

	private List<PayloadBay> withBays(PayloadBay... bays) {
		return Arrays.asList(bays);
	}

	private PayloadBay bay(int i, RestoringPayload restoringPayload) {
		return new PayloadBay(i, restoringPayload, 45_000);
	}

	private RestoringPayload withPayload(String id, String type, double charge, Instant rechargedAt) {
		return new RestoringPayload(id, type, charge, rechargedAt);
	}

}
