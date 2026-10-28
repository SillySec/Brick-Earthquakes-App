# Brick-Earthquakes-App

## Map and Google Maps API key

The Map tab uses Google Maps (Maps Compose) and needs a **Maps SDK for Android** API key.
The key is never committed:

1. In the Google Cloud console, create a project, enable **Maps SDK for Android**, and create
   an API key (restricting it to package `com.example.brickearthquakesapp` plus your debug
   keystore's SHA-1 is recommended).
2. Add this line to `local.properties` in the project root (the file is git-ignored):
   ```
   MAPS_API_KEY=your-key-here
   ```
   Alternatively set a `MAPS_API_KEY` environment variable before building.
3. Rebuild and run. Gradle injects the key into the manifest as a placeholder at build time.

Without a key the app still builds and the list works fully; the Map tab shows a "Map
unavailable" message instead. Use an emulator image that includes Google Play services.
A key that is present but invalid or restricted incorrectly produces a blank map rather than
that message (Google logs the authorization error in Logcat).

The map shows the same cached earthquakes as the list, opens on a world view, and does not use
or request the device location.

## Location (optional)

Location is an optional extra. The earthquake list, refresh and offline cache work the same
whether or not it is enabled, and nothing asks for it automatically.

- Tapping **Use my location** requests approximate (`ACCESS_COARSE_LOCATION`) permission.
  Fine and background location are never requested.
- Once a location is known, each row shows its distance from the user in miles. Without a
  location, rows simply omit the distance.
- Showing distances needs two things beyond the permission: location services turned on in
  the device settings, and an available location fix from Google Play Services. If either is
  missing, or the lookup times out, the app shows a short message with a **Try again**
  button, and the earthquake list is unaffected.
- The lookup is a single one-shot request (a fix up to 5 minutes old is accepted, with a
  10-second timeout). There is no continuous tracking.

**Testing note:** I could not confirm this feature end to end on Android emulators. On
several emulator images (Google Play and Google APIs), with a simulated location set through
Extended Controls and `adb emu geo fix`, the location request returned no fix, so the
"Couldn't determine your location" state was what I observed. I did not establish why, and I
have not verified the success path on a physical device. The distance calculation and the
location state handling are covered by unit tests with a fake location provider.
