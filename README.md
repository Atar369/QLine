# QLine

QLine is a real-time clinic queue demo built with Kotlin, Jetpack Compose, Firebase Authentication, and Cloud Firestore.

## Run the app

1. Open this project in Android Studio and sync Gradle (Android SDK 37 is required).
2. Keep the existing `app/google-services.json` in place; it must belong to the Firebase project you configure.
3. In Firebase Console, enable **Email/Password** and **Anonymous** sign-in under Authentication, and create a Firestore database.
4. Run the `app` configuration on an Android device or emulator. Sign in/create an account, or select **Continue as demo**.
5. Use the Customer and Staff tabs to take tickets and operate the demo queues.

The app seeds `queues/clinicA`, `queues/clinicB`, and `queues/clinicC` when missing. Queue state is stored in `queues`; ticket documents are stored in `tickets`. Staff controls are intentionally available to authenticated demo users; configure appropriate Firestore security rules and staff authorization before production use.
