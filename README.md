# Brick Earthquakes Tracker

A native Android app (Kotlin, Jetpack Compose, Material 3) for exploring recent earthquake
activity from the [USGS Earthquake API](https://earthquake.usgs.gov/earthquakes/feed/). Users can
browse earthquakes as a list or on a map, open details for any event, and, optionally, see how
far each one is from them. Previously loaded data stays available offline.

## Features

- **List and Map views** of the same data, switched with a toggle (List is the default).
  Switching never triggers a network request.
- **Details sheet** (Material 3 modal bottom sheet) opened by tapping a list row or a map marker:
  place, magnitude, date/time, depth (km), coordinates, and distance from the user when known.
- **Refresh** on launch and on demand, with progress, error and empty states.
- **Offline cache**: the last downloaded feed is stored in Room and shown immediately on every
  launch, including when there is no connection or the refresh fails.
- **Optional location**: tapping "Use my location" requests approximate location and adds
  distances in miles. The app is fully usable without it.

## Prerequisites and setup

| Requirement | Notes |
|---|---|
| Android Studio | A recent release that supports AGP 9.4.1. Developed with Android Studio build 261.26222.65 and its bundled JDK 25. |
| JDK 25 | `gradle/gradle-daemon-jvm.properties` asks for a JDK 25 daemon; Gradle downloads one automatically (Foojay resolver, needs network) if none is found. In Android Studio, the bundled JDK works. |
| Android SDK | Platform 37 (compileSdk/targetSdk 37). minSdk is 31 (Android 12). |
| Emulator or device | Android 12+ **with Google Play services** (needed by the map and by location). Use a "Google Play" or "Google APIs" system image. |
| Network access | For the USGS feed and Google Maps tiles. |
| Google Maps API key | Needed for the Map view only; see below. The list works without it. |

### Google Maps API key

The Map view needs a **Maps SDK for Android** key. The key is never committed.

1. In the Google Cloud console, create a project, enable **Maps SDK for Android**, and create an
   API key. Restricting it is recommended: *Application restrictions → Android apps* with package
   `com.example.brickearthquakesapp` and the SHA-1 of your debug keystore, and *API restrictions →
   Maps SDK for Android*.
2. Add this line to `local.properties` in the project root (the file is git-ignored):
   ```
   MAPS_API_KEY=your-key-here
   ```
   Alternatively, set a `MAPS_API_KEY` environment variable before building.
3. Rebuild. Gradle injects the key into the manifest as a placeholder at build time. (The key
   is therefore present in the built APK, which is why restricting it matters.)

Without a key the app still builds and runs: the list works fully and the Map view shows a
"Map unavailable" message instead of the SDK. A key that is present but invalid or wrongly
restricted produces a blank map instead; Google logs the authorization error in Logcat.

## Build, run and test

```bash
./gradlew assembleDebug          # build the debug APK  (Windows: gradlew.bat)
./gradlew installDebug           # install on a connected device/emulator
./gradlew testDebugUnitTest      # run the JVM unit tests
```

Or open the project in Android Studio and run the `app` configuration. If you run Gradle from a
terminal without a JDK on the `PATH`, point `JAVA_HOME` at Android Studio's bundled JDK.

## Architecture

Single Gradle module, MVVM, with a manual dependency container (no DI framework).

```
UsgsApi (Retrofit) ──► DefaultEarthquakeRepository ──► Room (EarthquakeDao)
                                  │                          │
                       refresh(): Result<Unit>      observeAll(): Flow
                                  └──────────┬───────────────┘
                                             ▼
                              EarthquakesViewModel  ◄── LocationProvider
                              StateFlow<EarthquakesUiState>
                                             │ collectAsStateWithLifecycle()
                                             ▼
              EarthquakesScreen ── List | Map ── shared selection ── details sheet
```

```
data/remote   UsgsApi, GeoJSON DTOs        data/local   Room entity, DAO, database
data          mappers, repository          location     LocationProvider, Fused impl, haversine
model         Earthquake                   ui           ViewModel, UI state, screen, list, map, detail sheet
BrickApp.kt   Application + AppContainer (manual DI)
```

- **Room is the single source of truth.** The UI only ever observes the database; the network
  only writes to it. Cached events therefore appear immediately and update automatically after
  any successful refresh.
- **One screen-level ViewModel** exposes an immutable `EarthquakesUiState` through `StateFlow`,
  collected in Compose with `collectAsStateWithLifecycle()`. The cache, the network refresh and
  the location lookup are independent: a slow or failing lookup or download never blocks the
  cached list.
- **Constructor injection.** `AppContainer` (lazily) builds Room, Retrofit and the repository;
  `MainActivity` hands them to the ViewModel through a factory. Repositories, the API, the DAO
  and the location provider are all replaceable with fakes in tests.
- **Selection state lives in Compose** (`rememberSaveable` in `EarthquakesScreen`) as an event
  **id**. The list and the map write to the same variable and a single bottom sheet reads the
  current event for that id from the latest data, so the sheet stays correct when a refresh
  changes the event, and closes when a refresh removes it.

## Offline caching and refresh

- The feed is `2.5_week.geojson`: magnitude 2.5+ events from the past 7 days, a few hundred
  events, newest first.
- A refresh runs on launch and when the user taps Refresh. A second request while one is in
  flight is ignored. There is no background refresh.
- A successful refresh replaces the whole cache with the new feed in **one Room transaction**
  (delete all, then insert), so the cache always mirrors the latest feed and the list never
  shows a half-updated table. The event id from USGS is the primary key.
- Parsing and mapping happen **before** the transaction, so a failed or unreadable response
  can never touch the existing cache.
- A single malformed GeoJSON feature (missing id/time/geometry, bad coordinates) is skipped; the
  rest of the feed is kept. If a feed has features but none are usable, it counts as a parse
  failure rather than wiping the cache.

| Situation | Behavior |
|---|---|
| First launch, no cache | Loading indicator, then the list |
| Refresh fails, cache exists | Cached list stays; a banner explains the failure and when the data is from, with Dismiss / Try again |
| Refresh fails, no cache | Error screen with Try again |
| Feed downloaded but contains no events | "No earthquakes found" |
| Nothing cached and nothing downloaded yet (e.g. error dismissed) | Neutral "No earthquake data yet" with Refresh |

Failures are modelled explicitly (`RefreshError`: network, HTTP status, unreadable response,
unknown). Coroutine `CancellationException` is always rethrown, never turned into an error.

## Location (optional)

Location is an optional extra. The list, the map, refresh and the offline cache work the same
whether or not it is enabled, and nothing asks for it automatically.

- Tapping **Use my location** requests approximate (`ACCESS_COARSE_LOCATION`) permission.
  Fine and background location are never requested. The permission request lives in the UI
  layer; the ViewModel only receives the outcome.
- Once a location is known, each list row and the details sheet show the distance in miles
  (great-circle/haversine, straight-line). Without a location, distances are simply omitted.
- If permission is denied, the app offers Try again, or Open settings once Android will no
  longer show the dialog. If location services are off, or no fix is available, a short message
  with Try again is shown. None of these states affect the earthquake list.
- Location is a single one-shot lookup (a fix up to 5 minutes old is accepted, 10-second
  timeout) through Google Play services' fused provider. There is no continuous tracking.
- The map does not use the device location: it shows earthquake markers only, with no
  my-location layer.

**Testing note:** I could not confirm the location success path on Android emulators. On
several emulator images (Google Play and Google APIs), with a simulated location set through
Extended Controls and `adb emu geo fix`, the location request returned no fix, so the
"Couldn't determine your location" state was what I observed. I did not establish why, and I
have not verified the success path on a physical device. The distance calculation and the
location state handling are covered by unit tests with a fake location provider.

## Testing

**Approach:** the logic that matters is kept out of Android classes (repository, mappers,
ViewModel, state, geo math, formatters) so it runs as fast JVM tests using hand-written fakes
and `kotlinx-coroutines-test`, with no mocking framework.

```bash
./gradlew testDebugUnitTest
```

86 unit tests cover:

- **Mapping/parsing:** coordinate order (GeoJSON is longitude, latitude, depth), null magnitude
  and place, skipping malformed features, an all-malformed feed.
- **Repository:** success replaces the cache; network, HTTP, parse and unexpected failures
  return a typed error and leave the cache untouched; cancellation propagates.
- **ViewModel and state:** cached data shown while refreshing, refresh success and failure with
  and without a cache, duplicate-refresh prevention, retry and dismissal, the distinction between
  loading, error, confirmed-empty and no-data, location outcomes (granted, denied, services off,
  unavailable, failure, cancellation), that location never blocks the list or refresh, and the
  selection rules used by the details sheet.
- **Haversine distances** (identical points, known city pairs, equator, antimeridian, poles) and
  the display formatters.

**Not covered by automated tests:** Compose UI, the map and marker taps, the permission dialog
flow, `FusedLocationProvider`, and the Room DAO against a real database (the one-transaction
guarantee relies on Room's `@Transaction`; the unit tests use a fake DAO). There are no
instrumented tests. Instead, I smoke-tested on an emulator: marker rendering, switching
between List and Map, selecting from both views, dismissing the sheet, rotation, and viewing
cached data offline. The location success path was not verified (see above).

## Key decisions and trade-offs

- **Room as source of truth** over caching in memory or serializing the response: it gives
  offline behavior, restart persistence and reactive updates for free.
- **Replace-all in one transaction** over upsert-and-prune: simpler, and the cache can never
  contain events the feed no longer reports.
- **Manual DI** over Hilt: one ViewModel and a handful of objects do not justify the extra
  plugin and setup.
- **Selection in Compose state, not the ViewModel**: it is purely presentational and keyed by
  id; the data it points at still comes from the ViewModel. Trade-off: it follows the screen's
  state, not the ViewModel's.
- **Bottom sheet instead of a detail screen**: no navigation library, one code path for list
  and map. Trade-off: no deep-linkable detail destination.
- **The map opens on a fixed world view** (the feed is global) and never moves on its own;
  marker taps are consumed so the camera stays put.
- **Pinned library versions.** The project's Kotlin 2.2.10 toolchain cannot read libraries built
  with Kotlin 2.4, so `kotlinx-serialization-json` is 1.9.0 and `maps-compose` is 8.0.0 (newer
  releases need Kotlin 2.4 and a newer Compose BOM). I kept the existing toolchain rather than
  upgrading it.
- **minSdk 31**: keeps the location and theming code simple; it narrows device reach.
- **Destructive Room migration**: the table only caches the feed, so a schema change wipes it
  instead of running a migration (without a fallback Room would crash). The database is also
  excluded from backups.

## Known limitations

- Location success was not verified on a device or emulator; it needs Play services, location
  services on, and an available fix. Distances are straight-line miles only (no unit toggle).
- The Map view needs a Maps API key and Play services. A wrongly restricted key shows a blank map.
- Hundreds of standard markers are drawn with no clustering, so a world view is crowded. Markers
  are less accessible than list rows; the list is the fully accessible path.
- No filtering or sorting (the list is newest first), and a fixed data window (M2.5+, 7 days).
- Data refreshes on launch and on demand only; no background updates. Relative times such as "3
  hours ago" are computed when drawn and do not tick.
- Mapping of the feed and the distance computation run on the main thread. With a few hundred
  events this is cheap, but it would not scale to much larger feeds.
- Only English strings; the application id is the template's `com.example...`.

## What I would improve with more time

- An instrumented Room test (in-memory database) for transaction atomicity, and Compose UI tests
  for list/map selection and the permission states.
- Verify location on a physical device, then revisit the lookup strategy based on what it shows.
- Marker clustering, plus filtering/sorting by magnitude, time and distance.
- Periodic background refresh with WorkManager and a "last updated" indicator.
- Inject dispatchers and move feed mapping and distance work off the main thread.
- A unit toggle (km/miles), and richer details (USGS event link, tsunami and felt reports are
  already stored but not shown).
- CI running the build, unit tests and lint.
