package mars.platform.logics;

/**
 * A payload.
 * 
 * @author mperrando
 *
 */
public class Payload {
	public final String type;
	public double charge = 1;
	public final String id;

	public Payload(String type, String id, double charge) {
		this.id = id;
		this.type = type;
		this.charge = charge;
	}

	public Payload(String type, String id) {
		this(type, id, 1);
	}

	public boolean charging() {
		return charge < 1d;
	}

	@Override
	public String toString() {
		return "Payload [type=" + type + ", charge=" + charge + ", id=" + id + "]";
	}

}
