package mars.tower.planner.dummy;

import java.util.Collection;
import java.util.LinkedList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.joda.time.Interval;

public class IntervalCalculator {
	public Collection<Interval> subtract(Collection<Interval> source, Collection<Interval> toSubtract) {
		return source.stream().flatMap(i -> sub(i, toSubtract.stream())).collect(Collectors.toList());

	}

	private Stream<Interval> sub(Interval itt, Stream<Interval> toSubtract) {
		final Collection<Interval> results = new LinkedList<>();
		final var remaining = toSubtract.reduce(itt, (it, s) -> {
			if (it.contains(s)) {
				final Interval firstPart = new Interval(it.getStart(), s.getStart());
				results.add(firstPart);
				final Interval remainingPart = new Interval(s.getEnd(), it.getEnd());
				it = remainingPart;
			} else if (s.overlaps(it)) {
				final Interval whatsLeft;
				if (s.getStartMillis() < it.getStartMillis()) {
					whatsLeft = new Interval(s.getEnd(), it.getEnd());
				} else {
					whatsLeft = new Interval(it.getStart(), s.getStart());
				}
				it = whatsLeft;
			}
			return it;
		});
		results.add(remaining);
		return results.stream().filter(i -> i.toDurationMillis() != 0);
	}
}
