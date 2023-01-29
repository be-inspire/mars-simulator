package mars.platform.logics;

import java.time.Instant;
import java.util.Optional;

public interface Bay<B extends Bay<B>> {

	int getId();

	Optional<Payload> getPayload();

	Instant getRestorationInstant();

	long getPrepareMillis();

	void addBayListener(BayListener<B> rechargeListener);

}
