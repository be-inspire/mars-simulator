package mars.tower;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Plan {

	private final Map<PitStop, Assignation> assignations = new HashMap<>();
	private final Collection<PitStop> plannables;

	public Plan(Collection<PitStop> planed) {
		this.plannables = planed;
	}

	public void add(PitStop p, Assignation assignation) {
		assignations.put(p, assignation);
	}

	public Map<PitStop, Assignation> assignations() {
		return Collections.unmodifiableMap(assignations);
	}

	public Collection<PitStop> getPlannables() {
		return Collections.unmodifiableCollection(plannables);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((assignations == null) ? 0 : assignations.hashCode());
		result = prime * result + ((plannables == null) ? 0 : plannables.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final Plan other = (Plan) obj;
		if (assignations == null) {
			if (other.assignations != null)
				return false;
		} else if (!assignations.equals(other.assignations))
			return false;
		if (plannables == null) {
			if (other.plannables != null)
				return false;
		} else if (!plannables.equals(other.plannables))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "Plan [planed=" + plannables + ", assignations=" + assignations + "]";
	}
}
