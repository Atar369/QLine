# QLine

QLine is a restaurant queue app built with Kotlin, Jetpack Compose, Firebase, Google Maps, and Places. Customers can browse nearby restaurants, view official partner queues, and share timestamped photos and estimates of physical lines at any restaurant. Only QLine-approved staff accounts can operate official queues.

## Run the app

1. Open this project in Android Studio and sync Gradle (Android SDK 37 is required).
2. Keep `app/google-services.json` configured for your Firebase project.
3. In Firebase Console, enable **Email/Password** and **Anonymous** sign-in, create Firestore, and enable Firebase Storage.
4. In Google Cloud Console, enable **Maps SDK for Android** and **Places API (New)**. Create an Android-restricted API key for this app's application ID and signing certificate.
5. Add `MAPS_API_KEY=your-key` to the root `local.properties` file. Do not commit the key; `local.properties` is ignored by Git.
6. After selecting the correct Firebase project in the CLI, deploy Firestore and Storage rules and the report index with `firebase deploy --only firestore:rules,firestore:indexes,storage`.
7. Add partner restaurant documents and staff memberships as described below, then run the `app` configuration.

The map uses the device's location only after the customer taps **Use my location** and grants permission. Without permission, customers can move the map manually; the initial search area is Hong Kong.

## Partner and staff provisioning

Only active restaurants in `partnerRestaurants` can use the official queue. Create a document whose ID is the restaurant's Google Place ID and whose fields include `name`, `cuisine`, `address`, `latitude`, `longitude`, and `active: true`. Staff accounts must first be created in Firebase Authentication. Then create `staffMemberships/{uid}` with a `restaurantIds` string array containing only approved partner document IDs. These documents can only be provisioned by trusted Firebase Console/Admin SDK access; the app cannot grant staff access. A staff account sees only its assigned partner restaurants and queue controls.

## Queue reports

Customers can submit a physical-line photo and optional people/wait estimates for any restaurant. Reports are stored in `queueReports`; images are stored under `queue-reports/{restaurantId}/{uid}/{reportId}`. Reports are labeled as customer-submitted and include a relative timestamp; reports older than two hours are marked as old. Firebase Storage enforces image MIME types and a 5 MiB limit. Configure Firebase Storage billing and access according to your Firebase project before accepting public reports.

Official queue state is stored in `queues`, and tickets in `tickets`. The queue's `waitingNumbers` field lets customers see their position without reading other customers' ticket documents. Existing clinic documents and tickets are not deleted or migrated.
