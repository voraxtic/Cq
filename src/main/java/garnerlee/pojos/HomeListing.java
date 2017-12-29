package garnerlee.pojos;


public class HomeListing {
	// Nullables
	public String	id;
	public Integer	price;
	public String	street;
	public Integer	bedrooms;
	public Double	bathrooms; // 2.5 baths.
	public Integer	sq_ft;
	public Double	lat;
	public Double	lng;

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("id: " + id + ", ");
		sb.append("price: " + price + ", ");
		sb.append("street: " + street + ", ");
		sb.append("bedrooms: " + bedrooms + ", ");
		sb.append("bathrooms: " + bathrooms + ", ");
		sb.append("sq_ft: " + sq_ft + ", ");
		sb.append("lat: " + lat + ", ");
		sb.append("long: " + lng);

		return sb.toString();
	}
}
