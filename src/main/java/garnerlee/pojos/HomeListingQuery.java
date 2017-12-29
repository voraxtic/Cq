package garnerlee.pojos;

public class HomeListingQuery {
	// Integer is nullable and assignable to bare ints. Gson doesn't mind.
	public Integer min_price;
	public Integer max_price;
	public Integer min_bed;
	public Integer max_bed;
	public Double min_bath;
	public Double max_bath;

	public Near near;
}
