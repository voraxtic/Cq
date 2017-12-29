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
information inside your service account file, named:

service-account.json

Info:
  https://firebase.google.com/docs/database/admin/start

# JUnit
Commands are as in JUnit tests. To actually run it on a raw command line, you'd
need to create the classpath (-cp bin:firebasebin:geofirebin:gsonbin ) to add
firebase, gson, etc. that exist only inside the IDE, not to mention use gradle
to grab appropriate versions.

So, just use JUnit tests.

	gradlew test

Eclipse: 
Cq arguments to Main:

	import listing-details.json
	query query.json
	query query.json output_results.json

Run import first. Then the rest of the tests. Delete.