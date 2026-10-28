# Brick-Earthquakes-App

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
