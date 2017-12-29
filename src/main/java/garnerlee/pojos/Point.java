package garnerlee.pojos;

public class Point extends Geometry {
	public double coordinates[];
	public Point(double lng, double lat) {
		type = Point.class.getSimpleName();
		coordinates = new double[2];
		coordinates[0] = lng;
		coordinates[1] = lat;
	}
}
