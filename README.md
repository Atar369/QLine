# QLine

QLine is a restaurant queue demo built with Kotlin, Jetpack Compose, Firebase Authentication, and Cloud Firestore. Customers can join or cancel a queue and see their live position; restaurant staff can call the next guest, pause or reopen the queue, and reset it.

## Run the app

1. Open this project in Android Studio and sync Gradle (Android SDK 37 is required).
2. Keep the existing `app/google-services.json` in place; it must belong to the Firebase project you configure.
3. In Firebase Console, enable **Email/Password** and **Anonymous** sign-in under Authentication, and create a Firestore database.
4. Run the `app` configuration on an Android device or emulator. Sign in/create an account, or select **Continue as demo**.
5. Use the Customer and Staff tabs to join a restaurant queue and operate it. The sample restaurants are currently defined in the app.

Queue state is stored in `queues`; ticket documents are stored in `tickets`. The app no longer creates clinic queues when a user signs in. It uses new restaurant IDs (`harbour-grill`, `central-noodles`, and `garden-table`) and creates each queue when needed. Existing clinic documents and tickets are not deleted or migrated. Staff controls are intentionally available to authenticated demo users; configure appropriate Firestore security rules and staff authorization before production use.
