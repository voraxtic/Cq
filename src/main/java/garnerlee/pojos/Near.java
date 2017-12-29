package garnerlee.pojos;

import com.google.gson.annotations.SerializedName;

public class Near {
	// This Gson annotation is necessary to meet JSON spec.
	@SerializedName("long")
	public Double lng;
	public Double lat;
	public Double radius;
}