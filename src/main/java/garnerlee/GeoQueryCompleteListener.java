package garnerlee;

import java.util.List;

import garnerlee.pojos.HomeListing;

public interface GeoQueryCompleteListener {
	void QueryComplete(List<HomeListing> list);
}
