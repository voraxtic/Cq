/**
 * Copyright 2017 Garner Lee
 * 
 */

package garnerlee;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.omg.CORBA.portable.ApplicationException;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.auth.UserRecord.CreateRequest;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

import garnerlee.pojos.Feature;
import garnerlee.pojos.FeatureCollection;
import garnerlee.pojos.HomeListing;
import garnerlee.pojos.HomeListingQuery;
import garnerlee.pojos.Point;
import garnerlee.pojos.Property;

public class Cq {

	private final static String LISTING_LOCATION = "listings";
	private final static String LISTING_GEOFIRE_LOCATION = "listings-geofire";

	private final static String DATABASE_URL = "https://cqproj-dd157.firebaseio.com"; // CQ Project URL.
	private static FirebaseDatabase fbInstance;
	private static DatabaseReference ref;
	private static DatabaseReference georef;

	// Firebase is mostly asynchronous. Command line needs to wait to display results.
	private long waitMS = 4000;
	private boolean waitingForFirebaseCompletion = false;

	static Cq cq;
	FirebaseApp fbapp;
	
	public Cq() {
		fbapp = initializeFirebase();
	}

	public static FirebaseApp initializeFirebase() {
		FirebaseApp appret = null;
		// Firebase apparently uses static class initialization.
		try {
			// [START initialize]
			FileInputStream serviceAccount = new FileInputStream("service-account.json"); // Do not check in. Default Filepath is same as project root.
			FirebaseOptions options = new FirebaseOptions.Builder()
					.setCredentials(GoogleCredentials.fromStream(serviceAccount))
					.setDatabaseUrl(DATABASE_URL)
					.build();
			appret = FirebaseApp.initializeApp(options);
			fbInstance = FirebaseDatabase.getInstance();
			// [END initialize]
		} catch (IOException e) {
			System.out.println("ERROR: invalid service account credentials. See README.");
			System.out.println(e.getMessage());

			System.exit(1);
		}
		
		return appret;

		// Shared Database server references for the options:
	}


	private long importCount = 0l;
	private long progressCount = 0l;
	private boolean readComplete = false;	
	/**
	 * doImport
	 * @param filename
	 * @param status
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private void doImport(String filename, InsertStatusListener status) throws FileNotFoundException, IOException {

		georef = fbInstance.getReference().child(LISTING_GEOFIRE_LOCATION);
		ref = fbInstance.getReference().child(LISTING_LOCATION);

		BufferedReader br = new BufferedReader(new FileReader(filename));

		String line = br.readLine(); // Reading header
		// Read IDs.
		HashMap<String, Integer> idList = new HashMap<String, Integer>();
		String[] cols = line.split(",");
		for (int n = 0; n < cols.length; n++) {
			idList.put(cols[n], n);
		}


		GeoFire.CompletionListener statusListener = new GeoFire.CompletionListener() {
			@Override
			public void onComplete(String key, DatabaseError error) {
				if (error != null) {
					System.err.println("There was an error saving the location to GeoFire: " + error);
				} else {
					System.out.println("Location " + key + " saved on server successfully!");
					progressCount++;
					if (readComplete == true &&
						importCount == progressCount) {
						status.InsertStatusDone();
					}
				}
			}
		};

		// It's possible to generically match abstract field names everywhere, but it's overkill.
		while ((line = br.readLine()) != null && !line.isEmpty()) {
			String[] fields = line.split(",");

			HomeListing l = new HomeListing(); // POJO
			l.id = fields[idList.get("id")];

			l.bathrooms = Double.parseDouble(fields[idList.get("bathrooms")]);
			l.bedrooms = Integer.parseInt(fields[idList.get("bedrooms")]);
			l.lat = Double.parseDouble(fields[idList.get("lat")]);
			l.lng = Double.parseDouble(fields[idList.get("lng")]);

			l.street = fields[idList.get("street")];

			l.price = Integer.parseInt(fields[idList.get("price")]);
			l.sq_ft = Integer.parseInt(fields[idList.get("sq_ft")]);        

			// Listings and GeoFire are *both* asynchronous

			// For listing.
			insertListing(l);

			// For geo lookup of IDs.
			insertListingGeo(l.id, new GeoLocation(l.lat, l.lng), statusListener);
			importCount++;
		}
		br.close();
		readComplete = true;


	}

	private void insertListing(HomeListing l) {
		// Insert below with provided key. push() if using generated id.
		DatabaseReference newHome = ref.child(l.id);
		newHome.setValueAsync(l);
	}

	// The Key here is that *same* listing ID is in both listings, and listing-geofire. So, query one,
	// Yields the other if queried. Now, this also means callbacks need to be set to
	// sync them.
	private void insertListingGeo(String listingId, GeoLocation geo, GeoFire.CompletionListener geoListener) {
		georef = fbInstance.getReference().child(LISTING_GEOFIRE_LOCATION);
		GeoFire geoFire = new GeoFire(georef);

		if (listingId == null) { // TODO: Throw.
			throw new IllegalArgumentException("API error: String listingId required.");
		}

		if (geo == null) {
			throw new IllegalArgumentException("API error: GeoLocation geo required for : " + listingId);
		}

		geoFire.setLocation(listingId, geo, geoListener);

	}

	/**
	 * doQuery
	 * @param filename of the formatted query json file.
	 * @param qListener
	 * @return
	 * @throws FileNotFoundException
	 */
	private GeoQuery doQuery(String filename, GeoQueryCompleteListener qListener)
			throws FileNotFoundException {
		System.out.println("Do Import on filename: " + filename);

		// Using Gson, as reading a file is not the most interesting thing.
		Gson gson = new Gson();
		JsonReader reader;
		reader = new JsonReader(new FileReader(filename));

		// Grab the nicely created POJO representation of the JSON. The POJO itself
		// needs the Gson's "@SerializedName" annotation map Near's "long"
		// field name to a legal Java field name "lng".
		HomeListingQuery query = gson.fromJson(reader, HomeListingQuery.class);

		// Do query:

		// Apparently ref listens to the same events.
		georef = fbInstance.getReference().child(LISTING_GEOFIRE_LOCATION);
		GeoFire geoFire = new GeoFire(georef);

		// As an admin, the app has access to read and write all data, regardless of Security Rules
		ref = fbInstance.getReference().child(LISTING_LOCATION);

		// "Near" is really GeoFire, and queried separately. Both it, and the subquery filter is hypothetically
		// against a realtime stream, and the subquery doesn't have a time limit.
		GeoQuery geoQuery = null;
		if (query.near != null) {
			if (query.near.lat == null || query.near.lng == null) {
				throw new IllegalArgumentException("GeoLocation queries requires both longitude and latitude fields specified.");
			}
			if (query.near.radius == null) {
				// Unspecified. Let GeoFire handle the warning message if it wants.
				query.near.radius = (double)Integer.MAX_VALUE;
			}
			geoQuery = geoFire.queryAtLocation(new GeoLocation(query.near.lat, query.near.lng), query.near.radius);
			queryWithLocation(geoQuery, query, qListener);

		} else if (query.near == null) {
			queryWithoutLocation(query, qListener);
		}

		return geoQuery;
	}

	// no LimitTo.
	// Here we ask, what happens if min > max. We don't second guess the caller. Firebase
	// will follow the math.
	private void queryWithoutLocation(HomeListingQuery query, GeoQueryCompleteListener qListener) {
		// Query listings for all IDs
		DatabaseReference listref = fbInstance.getReference().child(LISTING_LOCATION);
		List<HomeListing> list = Collections.synchronizedList(new ArrayList<HomeListing>());

		Query fbquery = buildQuery(query, listref);

		fbquery.addChildEventListener(new ChildEventListener() {

			@Override
			public void onChildAdded(DataSnapshot dataSnapshot, String prevChildKey) {
				HomeListing homeListing = dataSnapshot.getValue(HomeListing.class);
				boolean allowed = filterOneListing(query, list, homeListing);

				if (allowed) {
					System.out.println(dataSnapshot.getKey() +
							" matched to [$" + homeListing.price + 
							"] home: " + homeListing.street +
							" located: [" + homeListing.lat + ", " + homeListing.lng + "]");
				}
			}

			@Override
			public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
				System.out.println("onChildChanged [NOT IMPLEMENTED]");
			}

			@Override
			public void onChildRemoved(DataSnapshot snapshot) {
				System.out.println("onChildRemoved [NOT IMPLEMENTED]");
			}

			@Override
			public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
				System.out.println("onChildMoved [NOT IMPLEMENTED]");
			}

			@Override
			public void onCancelled(DatabaseError error) {
				System.out.println("onCancelled [NOT IMPLEMENTED]");
			}
		});


		// There's no "complete" callback interface, only that we potentially
		// asked the server nicely for data.
		qListener.QueryComplete(list);

	}

	// No location specified, so we'll hit the Firebase "listings" directly.
	// Pick ONE available orderByChild to use form HomeListingQuery, and
	// filter more upon that.
	// ID value is type String. This is because of GeoFire's String "KEY" parameter
	// as well as the example.
	private Query buildQuery(HomeListingQuery query, DatabaseReference listref) {
		String orderByKey = "id";
		Query fbquery = null;

		if (query.min_price != null) {  // Constrain Price:
			orderByKey = "min_price";
			fbquery = listref.orderByChild(orderByKey);
			fbquery.startAt(query.min_price);
			// Paired with:
			if (query.max_price != null) {
				fbquery.endAt(query.max_price);
			}
		} else if (query.max_price != null) {
			orderByKey = "max_price";
			// Paired with:
			fbquery = listref.orderByChild(orderByKey);
			if (query.min_price != null) {
				fbquery.startAt(query.min_price);

			}
			fbquery = fbquery.endAt(query.max_price);        	

		} else if (query.min_bed != null) { // Constrain bedrooms
			orderByKey = "min_bed";
			fbquery = listref.orderByChild(orderByKey);
			fbquery.startAt(query.min_bed);
			// Paired with:
			if (query.max_bed != null) {
				fbquery.endAt(query.max_bed);
			}
		} else if (query.max_bed != null) {
			orderByKey = "max_bed";
			// Paired with:
			fbquery = listref.orderByChild(orderByKey);
			if (query.min_bed != null) {
				fbquery.startAt(query.min_bed);

			}
			fbquery = fbquery.endAt(query.max_bed);        	

		} else if (query.min_bed != null) { // Constrain bathrooms
			orderByKey = "min_bed";
			fbquery = listref.orderByChild(orderByKey);
			fbquery.startAt(query.min_bed);
			// Paired with:
			if (query.max_bed != null) {
				fbquery.endAt(query.max_bed);
			}
		} else if (query.max_bed != null) {
			orderByKey = "max_bed";
			// Paired with:
			fbquery = listref.orderByChild(orderByKey);
			if (query.min_bed != null) {
				fbquery.orderByChild(orderByKey).startAt(query.min_bed);

			}
			fbquery = fbquery.endAt(query.max_bed);        	

		} else {
			fbquery = listref.orderByChild("id");
		}
		return fbquery;
	}

	// Right, if Firebase is a firehose stream. Only One orderby is allowed in the chain, or else it just
	// stops working, and it's not a where() clause. The onChildEvent looks at the stream for new results. 
	private void queryWithLocation(
			GeoQuery geoQuery,
			HomeListingQuery query,
			GeoQueryCompleteListener qListener) {

		List<HomeListing> list = Collections.synchronizedList(new ArrayList<HomeListing>());
		waitingForFirebaseCompletion = true;

		geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
			@Override
			public void onKeyEntered(String key, GeoLocation location) {
				System.out.println(String.format("Key %s found at [%f,%f]", key,
						location.latitude, location.longitude));

				// Query listings with the IDs, and add it to the results.
				DatabaseReference listref = fbInstance.getReference().child(LISTING_LOCATION);

				listref.orderByChild("id").equalTo(key).addChildEventListener(new ChildEventListener() {

					@Override
					public void onChildAdded(DataSnapshot dataSnapshot, String prevChildKey) {
						HomeListing homeListing = dataSnapshot.getValue(HomeListing.class);
						boolean allowed = filterOneListing(query, list, homeListing);

						if (allowed) {
							System.out.println(dataSnapshot.getKey() +
									" matched to [$" + homeListing.price + 
									"] home: " + homeListing.street +
									" located: [" + homeListing.lat + ", " + homeListing.lng + "]");
						}
					}

					@Override
					public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
						System.out.println("onChildChanged [NOT IMPLEMENTED]");
					}

					@Override
					public void onChildRemoved(DataSnapshot snapshot) {
						System.out.println("onChildRemoved [NOT IMPLEMENTED]");
					}

					@Override
					public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
						System.out.println("onChildMoved [NOT IMPLEMENTED]");
					}

					@Override
					public void onCancelled(DatabaseError error) {
						System.out.println("onCancelled [NOT IMPLEMENTED]");
					}
				});
			}

			@Override
			public void onKeyExited(String key) {
				System.out.println(String.format("Key %s is no longer in the search area", key));
			}

			@Override
			public void onKeyMoved(String key, GeoLocation location) {
				System.out.println(String.format("Key %s moved within the search area to [%f,%f]", key, location.latitude, location.longitude));
			}

			@Override
			public void onGeoQueryReady() {
				System.out.println("All initial data has been loaded and events have been fired!");
				waitingForFirebaseCompletion = false;
			}

			@Override
			public void onGeoQueryError(DatabaseError error) {
				System.err.println("There was an error with this query: " + error);
			}
		});
		// Wait for complete.
		while (waitingForFirebaseCompletion) {
			try {
				// Cq isn't a server/runnable, it's a command line that ends by itself.
				// Tighter Thread Object wait() --> notify() signaling is not applicable.
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		// Signal we're done with the GeoQuery portion of query.
		qListener.QueryComplete(list);	
	}
	
	private boolean  filterOneListing(HomeListingQuery query, List<HomeListing> list,
			HomeListing homeListing) {   	
		// Manual filters on "stream" if close listings. HomeListingQuery fields
		// should be nullable Objects.
		if (query.min_bed != null && (query.min_bed > homeListing.bedrooms)) {
			return false;
		}
		if (query.max_bed != null && (query.max_bed < homeListing.bedrooms)) {
			return false;
		}
		if (query.min_price != null && (query.min_price > homeListing.price)) {
			return false;
		}
		if (query.max_price != null && (query.max_price < homeListing.price)) {
			return false;
		}
		if (query.min_bath != null && (query.min_bath > homeListing.bathrooms)) {
			return false;
		}
		if (query.max_bath != null && (query.max_bath < homeListing.bathrooms)) {
			return false;
		}
		synchronized(list) {
			list.add(homeListing);
		}
		// Listing passed filters. Allowed.
		return true;
	}


	// doImport, doQuery 
	private boolean _cmdHandled = false;
	/**
	 * handleCommand is needed to allow unit/functional tests. Main returns void, which
	 * won't allow asserts. CompletableFuture is in Java8, but that's not Firebase API
	 * in use, so we'll have to use standard Listeners.
	 * @param args
	 * @return
	 */
	public static boolean handleCommand(String[] args) {

		if (args == null || args.length < 2) {
			System.out.println("cq import csvfilename");
			System.out.println("cq query query.json [result_file.json]");
			return true;
		}
		
		cq = new Cq();
		String action = args[0];
		String arg2 = args[1];



		//insertListingGeo("suttroTower", new GeoLocation(37.7552,122.4528));

		if (action.equals("import")) {
			// [IMPORT]
			try {
				String filename = arg2;
				cq.doImport(filename, new InsertStatusListener() {
					@Override
					public void InsertStatusDone() {
						// DONE! No timeout.
						cq.waitingForFirebaseCompletion = true;
						System.out.println("Insertion Done.");
						cq._cmdHandled = true;
					}
				});
				// Keep the JVM alive until Firebase tells status listener it's done.
				while(cq.waitingForFirebaseCompletion == false) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

		} else if (action.equals("query")) {
			// [QUERY]
			GeoQuery queryObject = null;
			try {
				String filename = arg2;
				queryObject = cq.doQuery(filename, new GeoQueryCompleteListener() {
					@Override
					public void QueryComplete(List<HomeListing> list) {
						System.out.println("Got a complete *geo* query.");
						String outputFileName = "home_listings_result.json";
						if (args.length == 3 && !args[2].equals(arg2)) {
							outputFileName = args[2];
						}
						// GeoQuery has a signal for complete. The check for matching listings is against a realtime
						// stream which has no end (or even, no data at all). Let's wait a sane number of MS for
						// some results before ending the user's query command.
						try {
							System.out.println("Waiting on Firebase data stream for results for " + cq.waitMS + " milliseconds.");
							Thread.sleep(cq.waitMS);
							cq.outputJsonResult(list, outputFileName);
							cq._cmdHandled = true;
						} catch (InterruptedException e) {
							e.printStackTrace();
						} catch (Exception e ) {
							e.printStackTrace();
						}
					}
				});

				System.out.print("\n");
				System.out.println("Query Waiting Ended.");
				// Mild cleanup.
				if (queryObject != null) {
					queryObject.removeAllListeners();
				}

			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}

		} else if (action.equals("wipe")) {
			try {
				if (!arg2.equals("IAMREALLYSURE")) {
					System.out.println("wipe IAMREALLYSURE required to wipe.");	
				} else {
					doWipe();
					System.out.println("Wiping. Wait for " + cq.waitMS + " milliseconds.");
					Thread.sleep(6000);
					cq._cmdHandled = true;
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
		} else if (action.equals("createuser")) {
			// [CREATE A USER] (though AdminSDK API has full R/W access.)
			CreateRequest request = new CreateRequest()
					.setEmail("emailly@gmail.com")
					.setEmailVerified(false)
					.setPassword("")
					.setPhoneNumber("+11234567890")
					.setDisplayName("Garner Lee")
					.setPhotoUrl("http://www.example.9991203492314987123.com/12345678/photo.png")
					.setDisabled(false);

			UserRecord userRecord = null;
			try {
				userRecord = FirebaseAuth.getInstance().createUserAsync(request).get();
				System.out.println("Successfully created new user: " + userRecord.getUid());
				cq._cmdHandled = true;
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (ExecutionException e) {
				e.printStackTrace();
			}
		}

		fbInstance.goOffline();
		// This is supposed to kill all daemon threads to allow the JVM to exit (or move to the next test),
		// but it doesn't until supposedly fixed in Firebase Admin SDK v5.4.
		cq.fbapp.delete();
		//cq.fbapp = null; // Deader-er.
		
		return cq._cmdHandled;
	}

	
	private static void doWipe() {
		// Google Futures API...in future. We're a command line, so we'd need also return
		// a future and let the caller hang round until the future threads return.
		// Or just fire and forget.
		ref = fbInstance.getReference().child(LISTING_LOCATION);
		ApiFuture<Void> fv1 = ref.removeValueAsync();

		georef = fbInstance.getReference().child(LISTING_GEOFIRE_LOCATION);
		ApiFuture<Void> fv2 = georef.removeValueAsync();
		return;
	}

	/**
	 * main. This is the entry point for the Challenge problem command line.
	 * @param args
	 */
	public static void main(String args[]) {
		boolean cmdResult = Cq.handleCommand(args);
		System.out.println("Command handled: " + cmdResult);

		// Kill off any remaining FirebaseApp daemon threads.
		//System.exit(0);
	}

	/**
	 * outputJsonResult output to problem spec, which is FeatureCollection.
	 * @param list A java List of HomeListings.
	 * @param outputFilename Where to output the JSON result
	 * @throws Exception 
	 */
	protected void outputJsonResult(List<HomeListing> list, String outputFilename) throws Exception {
		System.out.println("--------------");
		System.out.println("outputResult");
		System.out.println("--------------");

		if (list == null) {
			throw new IllegalArgumentException("There's no list given to export");
		}
		
		Writer writer = new FileWriter(outputFilename);
		try {
			FeatureCollection fc = new FeatureCollection();
			fc.features = new ArrayList<Feature>();

			synchronized (list) {

				for (HomeListing item : list) {
					System.out.println(item.toString());
					Feature f = new Feature();

					// Longitude, then Latitude for GeoJSON.
					f.geometry = new Point(item.lng, item.lat);

					f.properties = new Property();
					f.properties.id = item.id;
					f.properties.price = item.price;
					f.properties.street = item.street;
					f.properties.bedrooms = item.bedrooms;
					f.properties.bathrooms = item.bathrooms;
					f.properties.sq_feet = item.sq_ft;
					fc.features.add(f);
				}
				System.out.println("--------------");
				System.out.println("Size of list: " + list.size());
			}
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			gson.toJson(fc, writer);
		} catch (Exception e) {
			throw e;
		} finally {
			// No more space, IO exception, etc. Close it.
			writer.close();
		}
	}

}
