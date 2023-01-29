package mars.platform.sim;

import static java.time.temporal.ChronoUnit.MILLIS;

import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eventloop.El;
import eventloop.Timeout;
import mars.platform.logics.Bay;
import mars.platform.logics.BayListener;
import mars.platform.logics.Payload;
import mars.utils.ListenerNotifier;

/**
 * A simulative, configurable bay.
 * 
 * @author mperrando
 *
 */
public class SimulativeBay implements Bay<SimulativeBay> {
	private final static Logger LOGGER = LoggerFactory.getLogger(Bay.class);

	public SimulativeBay(int id, Optional<Payload> payload, double chargePerSecond, long prepareMillis) {
		if (payload == null)
			throw new NullPointerException();
		this.prepareMillis = prepareMillis;
		this.chargePerSecond = chargePerSecond;
		this.id = id;
		this.payload = payload;
	}

	private Optional<Payload> payload = Optional.empty();
	public final int id;
	private final ListenerNotifier<BayListener<SimulativeBay>> listenerNotifier = new ListenerNotifier<>();
	private Timeout rechargingTimeout = Timeout.NULL;
	private final double chargePerSecond;
	public final long prepareMillis;

	@Override
	public Optional<Payload> getPayload() {
		return payload;
	}

	@Override
	public Instant getRestorationInstant() {
		return payload.filter(Payload::charging).map(this::endOfCharge).orElse(null);
	}

	public void put(Payload payload) {
		if (this.payload.isPresent())
			throw new IllegalStateException("Bay already occupied by " + this.payload);
		if (payload == null)
			throw new NullPointerException("Null payload");
		this.payload = Optional.of(payload);
		listenerNotifier.notifyListeners(l -> l.bayContentUpdate(this));
		LOGGER.info("Putting payload: {} in charge in: {}", payload, this);
		if (payload.charge < 1)
			recharge1();
	}

	private void recharge1() {
		LOGGER.debug("Recharging up to 1%");
		final var missing = 1 - payload.get().charge;
		if (missing > 0.01) {
			recharge(.01);
		} else if (missing > 0) {
			recharge(missing);
		} else {
			LOGGER.info("Payload: {} fully charged in {}", payload, this);
			listenerNotifier.notifyListeners(l -> l.rechargeComplete(this));
		}
	}

	private void recharge(double charge) {
		LOGGER.debug("Recharging {}", charge);
		final var millis = timeForCharge(charge);
		LOGGER.debug("{} will be recharged in {} ms", charge, millis);
		rechargingTimeout = El.setTimeout(millis, () -> {
			payload.get().charge += charge;
			LOGGER.debug("Charge update {} of {}", payload.get().charge, payload);
			recharge1();
		});
	}

	private Instant endOfCharge(Payload p) {
		return El.now().plus(timeForCharge(1 - p.charge), MILLIS);
	}

	private long timeForCharge(double charge) {
		return (long) (charge / chargePerSecond * 1000);
	}

	public Payload remove() {
		if (this.payload.isEmpty())
			throw new IllegalStateException("No payload to remove");

		rechargingTimeout.cancel();
		try {
			return payload.get();
		} finally {
			this.payload = Optional.empty();
			listenerNotifier.notifyListeners(l -> l.bayContentUpdate(this));
		}
	}

	@Override
	public void addBayListener(BayListener<SimulativeBay> listener) {
		listenerNotifier.addListener(listener);
	}

	@Override
	public int getId() {
		return id;
	}

	@Override
	public long getPrepareMillis() {
		return prepareMillis;
	}

	@Override
	public String toString() {
		return "Bay [id=" + id + ",payload=" + payload.map(Object::toString).orElse("NONE") + ", chargePerSecond="
				+ chargePerSecond + ", restorationInstant=" + getRestorationInstant() + "]";
	}
}
