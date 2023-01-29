package mars.messages;

/**
 * A terrestrial point represented in longitude and latitude in some coordinate
 * system.
 *
 * @param lon the longitude of this point
 * @param lat the latitude of this point
 *
 * @author mperrando
 *
 */
public record GeoCoord(double lon, double lat) {
	static public final GeoCoord NULL = new GeoCoord(0, 0);
}
