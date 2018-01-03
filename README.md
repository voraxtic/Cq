# Challenge Question.

# Usage Notes
Firebase needs a few rules for indexing after table creation:

{
  "rules": {
    ".read": "auth != null",
    ".write": "auth != null",
    "listings": {
      ".indexOn": "id"
    },
    "listings-geofire": {
      ".indexOn": "g"
    }
  }
}

Also, you need to create a user for Admin SDK as well, since it's going to be
reading and writing privileged info. Put it into this file at the eclipse
project root. You should see a private key, as well as project specific
information inside your Firebase service account file. For this project,
it should be named:

service-account.json

# Security rules:
Requires Firebase user with AdminSDK use privileges. This is not a server.


Info:
  https://firebase.google.com/docs/database/admin/start

# JUnit
Commands are as in JUnit tests. To actually run it on a raw command line, you'd
need to create the classpath (-cp bin:firebasebin:geofirebin:gsonbin ) to add
firebase, gson, etc. that exist only inside the IDE, not to mention use gradle
to grab appropriate versions to add to the java classpath.

So, just use JUnit tests.

	gradlew test

Eclipse:
	Cq arguments to Main:

	import listing-details.json
	query query.json
	query query.json output_results.json

Run import first. Then the rest of the tests. Delete.

# Eclipse
Because it's sometimes refuses to start and crashes, in the workspace
(not project workspace), there's a ".metadata" directory. If all else fails, and
verbose turns up nothing actionable, rename it, and start eclipse again. Force a
gradle refresh.

# API implementation notes
- FutureAPIs are not being used, as they are quite new.
- Cloud FireStore has *native* geolocation support. At present, it's in Beta.
  Currently, 1/2/2018, there doesn't appear to be a transaction interface
  in GeoFire for listings, listings-geofire in the Firebase DB. Not ideal,
  but Firebase appears to be working on a better long term solution. Looking
  for an official statement.
- The "bathrooms" datatype has been made a Double. 2.5 baths is a real world
  number.
