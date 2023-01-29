package mars.platform.sim.dummy;

import static java.util.Optional.empty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cellply.invosys.ComSystem;
import com.cellply.invosys.signalling.SignalDispatcher;

import eventloop.El;
import eventloop.EventLoop.Event;
import eventloop.Timeout;
import mars.messages.CannotReadyPayload;
import mars.platform.logics.Bay;
import mars.platform.logics.Payload;
import mars.platform.logics.PlatformAutomationListener;
import mars.platform.logics.PlatformAutomationSystem;
import mars.platform.sim.SimulativeBay;
import mars.side.signals.DroneLandedEvent;
import mars.side.signals.DroneTookOffEvent;
import mars.side.signals.Events;
import mars.side.signals.PayloadOnBoard;
import mars.signals.Signals;
import mars.utils.ListenerNotifier;

/**
 * A simulated, configurable {@link PlatformAutomationSystem} implementation.
 * 
 * @author mperrando
 *
 */
public class DummyAutomationSystem implements PlatformAutomationSystem<SimulativeBay> {

	private final static Logger LOGGER = LoggerFactory.getLogger(DummyAutomationSystem.class);
	private Optional<Payload> readyPayload = empty();
	private String droneOnFlange;
	private final SignalDispatcher signalDispatcher;
	private final String myPltName;
	private final ListenerNotifier<PlatformAutomationListener> listenerNotifier = new ListenerNotifier<>();
	private final Consumer<Event> executor;
	private final List<SimulativeBay> payloadBays = new ArrayList<>();
	private int exchangingBay;
	private Optional<PayloadOnBoard> payloadOnBoard;
	private Optional<Timeout> preparingTimeout = empty();
	private Payload movingPayload;
	private Optional<Timeout> unpreparingTimeout = empty();
	private long serviceMillis = 0L;

	public DummyAutomationSystem(ComSystem comSystem, String myPltName, Consumer<Event> executor) throws IOException {
		this.executor = executor;
		this.myPltName = myPltName;
		signalDispatcher = comSystem.createSignalSubscriber(Signals.EVENT.name(), "mars.drone.#");
		signalDispatcher.register(Events.MARS_DRONE_LANDED, DroneLandedEvent.class, this::onDroneLanded);
		signalDispatcher.register(Events.MARS_DRONE_TOOK_OFF, DroneTookOffEvent.class, this::onDroneTookOff);
	}

	@Override
	public long getServiceTime() {
		return serviceMillis;
	}

	public void setServiceMillis(long serviceMillis) {
		this.serviceMillis = serviceMillis;
	}

	@Override
	public CompletionStage<Void> startPitStop(Runnable onPsUnprepared) {
		final var payloadToUse = readyPayload.orElseGet(() -> {
			LOGGER.error("Cannot start PS because no payload is ready");
			throw new RuntimeException("No payload prepared");
		});
		final Bay<?> bay = getBay(exchangingBay);
		if (bay.getPayload().isPresent()) {
			LOGGER.error("Cannot proceed because bay: {} is NOT empty", exchangingBay);
			throw new RuntimeException("Bay " + exchangingBay + " is NOT empty");
		}
		LOGGER.info("Starting PitStop with payload: {} service time: {}", payloadToUse, serviceMillis);
		final long endsInMillis = serviceMillis;
		final CompletableFuture<Void> cf = new CompletableFuture<Void>();
		El.setTimeout(endsInMillis, () -> onServiceFinished(payloadToUse, bay, cf, onPsUnprepared));
		return cf;
	}

	private void onServiceFinished(final Payload payloadToUse, final Bay<?> bay, final CompletableFuture<Void> cf,
			Runnable onPsUnprepared) {
		LOGGER.info("Finished PitStop with payload: {}", payloadToUse);
		cf.complete(null);
		final PayloadOnBoard pob = payloadOnBoard.orElseGet(() -> {
			throw new RuntimeException("Expected a payload on board");
		});
		this.readyPayload = Optional.of(new Payload(pob.payloadType(), pob.id(), pob.charge()));
		unpreparePitStop(onPsUnprepared);
	}

	@Override
	public String getLandedDroneId() {
		return droneOnFlange;
	}

	@Override
	public void preparePayload(final int bayId, Runnable onPsReady) throws CannotReadyPayload {
		LOGGER.info("Readying payload from bay: {}", bayId);
		if (unpreparingTimeout.isPresent()) {
			LOGGER.error("Cannot ready payload beacuse another is unpreparing");
			throw new CannotReadyPayload("Another payload is unpreparing");
		}
		if (readyPayload.isPresent()) {
			LOGGER.error("Cannot ready payload beacuse another is ready");
			throw new CannotReadyPayload("Another payload has been prepared");
		}
		final var bay = getBay(bayId);
		final var payload = bay.getPayload();
		if (payload.isEmpty()) {
			LOGGER.error("Cannot ready payload beacuse bay: {} is empty", bayId);
			throw new CannotReadyPayload("Bay " + bayId + " is empty");
		}
		bay.remove();
		movingPayload = payload.get();
		this.exchangingBay = bayId;
		final long payloadMovementMillis = payloadMovementMillis(bay);
		LOGGER.info("Start moving payload: {} from bay:{} to flange in {}", movingPayload, this.exchangingBay,
				payloadMovementMillis);
		preparingTimeout = Optional.of(El.setTimeout(payloadMovementMillis, () -> {
			LOGGER.info("Finished moving payload: {} from bay to flange: ", movingPayload, this.exchangingBay);
			putInFlange(movingPayload);
			LOGGER.info("Payload: {} form bay: {} is ready", movingPayload, this.exchangingBay);
			movingPayload = null;
			preparingTimeout = empty();
			onPsReady.run();
		}));
	}

	private SimulativeBay getBay(int bayId) {
		return payloadBays.stream().filter(b -> b.id == bayId).findAny().get();
	}

	@Override
	public void abortPitStop(Runnable r) {
		LOGGER.info("Aborting pitstop");
		unpreparePitStop(r);
	}

	private void unpreparePitStop(Runnable onPsUnprepared) {
		if (unpreparingTimeout.isPresent()) {
			LOGGER.info("Already unpreparing, ignoring command");
			return;
		}
		final var bay = getBay(exchangingBay);
		if (bay.getPayload().isPresent()) {
			LOGGER.error("Cannot unprepare bay: {} is NOT empty", exchangingBay);
			throw new RuntimeException("Bay " + exchangingBay + " is NOT empty");
		}
		if (readyPayload.isEmpty()) {
			LOGGER.error("Cannot unprepare, none is ready");
			throw new RuntimeException("Cannot unprepare payload because none is ready");
		}
		preparingTimeout.ifPresentOrElse(t -> {
			LOGGER.info("Unpreparing current preparing payload: " + movingPayload);
			t.cancel();
			preparingTimeout = empty();
		}, () -> {
			movingPayload = readyPayload.get();
			readyPayload = empty();
			LOGGER.info("Flange is free");
			onPsUnprepared.run();
		});
		final long payloadMovementMillis = payloadMovementMillis(bay);
		LOGGER.info("Start moving payload: {} from flange to bay: {} in {}", movingPayload, bay, payloadMovementMillis);
		unpreparingTimeout = Optional.of(El.setTimeout(payloadMovementMillis, () -> {
			LOGGER.info("Finished moving payload: {} from flange to bay: ", movingPayload, bay);
			bay.put(movingPayload);
			movingPayload = null;
			unpreparingTimeout = empty();
		}));
	}

	private void onDroneLanded(String category, String id, String agentName, DroneLandedEvent e) {
		executor.accept(() -> {
			LOGGER.info("Drone landed EVENT: {}", e);
			if (myPltName.equals(e.pltName())) {
				if (droneOnFlange == null) {
					droneOnFlange = e.droneId();
					payloadOnBoard = Optional.of(e.payloadOnBoard());
					listenerNotifier.notifyListeners(l -> l.droneLanded(e.droneId()));
				} else {
					LOGGER.error("There is already a drone on flange: {}, cannot land drone: {}", droneOnFlange,
							e.droneId());
				}
			} else {
				LOGGER.info("Ignoring because it I am {} and drone landed on {}", myPltName, e.pltName());
			}
		});
	}

	private void onDroneTookOff(String category, String id, String agentName, DroneTookOffEvent e) {
		executor.accept(() -> {
			LOGGER.info("Drone took off EVENT: {}", e);
			if (myPltName.equals(e.pltName())) {
				if (e.droneId().equals(droneOnFlange)) {
					droneOnFlange = null;
					payloadOnBoard = empty();
					listenerNotifier.notifyListeners(l -> l.droneTookOff(e.droneId()));
				} else {
					LOGGER.error("There is no drone on flange: {}, it is not possible that took off drone: {}",
							droneOnFlange, e.droneId());
				}
			}
		});
	}

	@Override
	public void addPlatformAutomationListener(PlatformAutomationListener l) {
		listenerNotifier.addListener(l);
	}

	@Override
	public List<SimulativeBay> getPayloadBays() {
		return payloadBays;
	}

	private void putInFlange(final Payload payload) {
		LOGGER.info("Flange has payload {}", payload);
		this.readyPayload = Optional.of(payload);
	}

	private long payloadMovementMillis(SimulativeBay bay) {
		return bay.prepareMillis;
	}
}
