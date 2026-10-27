# Scam Heat and Alert System

An Android application built with Java and Android Studio to help users report and check suspected scams in India.

## Features

- User registration, login, and profile management
- Community scam reporting with supporting evidence
- Scam checks for suspicious identifiers
- Google Maps-based scam map and location-aware functionality
- Scam statistics and intelligence views
- Admin dashboard for report review and user/report management
- Firebase Cloud Messaging notification support
- Supabase-backed data storage and reporting

## Tech stack

- Java and Android SDK
- Android Studio / Gradle (Kotlin DSL build files)
- Firebase Authentication and Cloud Messaging
- Supabase REST API
- Google Maps SDK and Google Play Services Location
- Material Components and MPAndroidChart

## Open the project

1. Download or clone this repository.
2. Open the `Scam-Heat-and-Alert-System` folder in Android Studio.
3. Allow Android Studio to sync Gradle and install any required Android SDK components.
4. Configure the Google Maps API key as described below.
5. Run the `app` configuration on an emulator or Android device.

## Configure Google Maps API key

The real local `secrets.properties` file is intentionally excluded from version control because it contains a Google Maps API key.

1. Copy `secrets.properties.example` to a new file named `secrets.properties` in the project root.
2. Replace `YOUR_GOOGLE_MAPS_API_KEY` with your own Google Maps API key.
3. Restrict the key in Google Cloud Console to the required Android app (package name `com.example.scamheat` and your signing certificate SHA-1) and only the APIs you use.
4. Android Studio creates `local.properties` for your local Android SDK path. Do not commit that file.

## Firebase configuration

The project currently includes `app/google-services.json` for its configured Firebase Android app. If you use a different Firebase project, download that project's Android `google-services.json` from the Firebase Console and replace the file. Never add Firebase Admin SDK service-account JSON files to this repository.

## Supabase configuration

The Supabase project URL and a **publishable** key are referenced in `app/src/main/res/values/strings.xml`. Publishable keys are intended for client-side use, but access must still be protected with correct Supabase Row Level Security (RLS) policies. Never put a Supabase secret/service-role key in an Android app or public repository. Review the RLS policies and database permissions before making the repository public.

## Notes

- Generated build output, Gradle caches, IDE-local state, and local key files have been excluded to keep the repository small and suitable for GitHub.
- The project was prepared for source control; this packaging step does not independently verify that the app builds in a fresh Android Studio environment.
