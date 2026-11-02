# Brick Earthquakes Tracker

A native Android app (Kotlin, Jetpack Compose, Material 3) for exploring recent earthquakes from the
[USGS Earthquake API](https://earthquake.usgs.gov/earthquakes/feed/). Browse them as a list or on a
map, open details for any event, and optionally see how far each one is from you. Previously loaded
data stays available offline.

## Features

- **List and Map** views of the same data, switched with a toggle (List is the default). Switching
  never triggers a network request.
- **Details sheet** (Material 3 modal bottom sheet) from a list row or a map marker: place, magnitude,
  time, depth (km), coordinates, and distance from the user when known.
- **Refresh** on launch and on demand, with loading, error and empty states.
- **Offline cache:** the last downloaded feed is stored in Room and shown immediately on every launch.
- **Optional location:** "Use my location" adds distances in miles. The app is fully usable without it.

## Setup

- **Android Studio** recent enough for AGP 9.4.1 (built with build 261.26222.65 and its bundled JDK 25).
- **JDK 25:** `gradle/gradle-daemon-jvm.properties` requests it. Gradle downloads one if none is found
  (needs network); Android Studio's bundled JDK also works.
- **Android SDK platform 37.** minSdk is 31 (Android 12).
- **Emulator or device on Android 12+ with Google Play services** (required by the map and by location).
- **A Google Maps API key** for the Map view (below). The list works without one.

### Google Maps API key

The Map view needs a **Maps SDK for Android** key. The key is never committed.

1. In the Google Cloud console, create a project, enable **Maps SDK for Android**, and create a key.
   Restrict it: *Application restrictions → Android apps* with package
   `com.example.brickearthquakesapp` and your debug keystore's SHA-1 (find it with
   `./gradlew signingReport`), and *API restrictions → Maps SDK for Android*.
2. Add to `local.properties` in the project root (git-ignored): `MAPS_API_KEY=your-key-here`
   (or set a `MAPS_API_KEY` environment variable).
3. Rebuild. Gradle injects the key into the manifest at build time, so it is present in the built
   APK, which is why restricting it matters.

Without a key the app still builds and the list works; the Map view shows "Map unavailable". A key
that is present but invalid or wrongly restricted gives a blank map instead (the authorization error
is logged in Logcat).

## Build, run, and test

```bash
./gradlew assembleDebug        # build (Windows: gradlew.bat)
./gradlew installDebug         # install on a connected device or emulator
./gradlew testDebugUnitTest    # run the JVM unit tests
```

Or run the `app` configuration in Android Studio. From a terminal without a JDK on `PATH`, point
`JAVA_HOME` at Android Studio's bundled JDK.

## Architecture

Single Gradle module, MVVM, manual dependency container (no DI framework).

```
UsgsApi (Retrofit) ──► DefaultEarthquakeRepository ──► Room (EarthquakeDao)
                                  │ refresh(): Result<Unit>     │ observeAll(): Flow
                                  └──────────────┬──────────────┘
                                                 ▼
                       EarthquakesViewModel ◄── LocationProvider
                       StateFlow<EarthquakesUiState>
                                                 │ collectAsStateWithLifecycle()
                                                 ▼
             EarthquakesScreen: List | Map ── shared selection ── details sheet
```

Packages: `data/remote`, `data/local`, `data` (mappers, repository), `location`, `model`, and `ui`
(ViewModel, state, screen, list, map, detail sheet). `BrickApp` holds the lazy `AppContainer`.

- **Room is the single source of truth.** The UI only observes the database; the network only writes
  to it, so cached events show immediately and update after any successful refresh.
- **MVVM with one screen-level ViewModel** exposing an immutable `EarthquakesUiState` as `StateFlow`,
  collected lifecycle-aware in Compose. The cache, the refresh and the location lookup are independent,
  so a slow or failing one never blocks the cached list.
- **Manual DI via constructors** rather than Hilt: one ViewModel and a handful of objects don't justify
  the extra setup, and the repository, API, DAO and location provider are swappable with fakes.
- **Selection is Compose state** (`rememberSaveable` in `EarthquakesScreen`) holding an event **id**.
  The list and the map write the same variable, and one bottom sheet looks up the current event for that
  id, so it reflects refreshed data and closes if a refresh removes the event. Trade-off: it follows
  the screen's state rather than the ViewModel's, and there is no deep-linkable detail destination.

## Offline caching and refresh

- The feed is `2.5_week.geojson` (magnitude 2.5+, past 7 days, a few hundred events, newest first).
  The USGS event id is the primary key.
- Refresh runs on launch and on demand; a request while one is in flight is ignored.
- A successful refresh replaces the whole cache in **one Room transaction** (delete all, insert all),
  so the cache mirrors the latest feed and never shows a half-updated table. Parsing happens before
  the transaction, so a failed or unreadable response cannot touch the existing cache.
- A single malformed GeoJSON feature is skipped. A feed with features but none usable counts as a
  parse failure rather than wiping the cache.
- If a refresh fails (network, HTTP status, unreadable response, other), cached events stay visible
  with a banner showing the reason and age of the data, plus Dismiss / Try again. With no cache,
  there is an error screen with Try again. An empty successful feed shows "No earthquakes found".
- `CancellationException` is always rethrown, never turned into an error.

## Location (optional)

- Tapping **Use my location** requests approximate (`ACCESS_COARSE_LOCATION`) permission; fine and
  background location are never requested. Nothing asks automatically, and the permission request
  lives in the UI layer (the ViewModel only receives the outcome).
- Distances (great-circle miles) appear in list rows and the details sheet. Without a location they
  are omitted. The map shows markers only and does not use the device location.
- Denied permission offers Try again, then Open settings once Android won't show the dialog. Location
  services off or no fix shows a short message with Try again. None of these affect the earthquake list.
- It is a single one-shot lookup through Google Play services' fused provider (a fix up to 5 minutes
  old is accepted, 10-second timeout), with no continuous tracking.

**Verification:** I verified the permission flow, location retrieval, and distance display on a
physical Android device. My emulator tests did not return a location fix despite configuring simulated
coordinates; the cause was not established.

## Testing

Core business logic is tested with JVM tests, hand-written fakes, and `kotlinx-coroutines-test`
(no mocking framework). The 86 unit tests cover:

- **Parsing and mapping:** GeoJSON coordinate order (longitude, latitude, depth), nullable fields,
  skipping malformed features.
- **Repository:** success replaces the cache; network, HTTP, parse and unexpected failures return a
  typed error and leave the cache untouched; cancellation propagates.
- **ViewModel and state:** cached data shown while refreshing, refresh success/failure with and without
  a cache, duplicate-refresh prevention, loading vs. error vs. confirmed-empty vs. no-data, location
  outcomes (granted, denied, services off, unavailable, failure, cancellation), location never blocking
  the list or refresh, and the selection rules used by the details sheet.
- **Haversine distances** and the display formatters.

There are **no instrumented or Compose UI tests**, and **no Room integration tests against a real
database**; the one-transaction guarantee relies on Room's `@Transaction`, and the unit tests use a fake
DAO. Manually, I smoke-tested on an emulator: marker rendering, List/Map switching, selecting from both
views, dismissing the sheet, rotation, and cached data offline; location was verified on a device (above).

## Decisions, trade-offs and limitations

- **Replace-all in one transaction** over upsert-and-prune: simpler, and the cache never holds events
  the feed no longer reports.
- **Pinned library versions:** the project's Kotlin 2.2.10 toolchain can't read libraries built with
  Kotlin 2.4, so `kotlinx-serialization-json` is 1.9.0 and `maps-compose` is 8.0.0. I kept the existing
  toolchain rather than upgrading it.
- **Destructive Room migration:** the table only caches the feed, so a schema change wipes it (and the
  database is excluded from backups). Offline right after such an update there is nothing cached.
- **Map opens on a fixed world view** (the feed is global); marker taps are consumed so the camera
  never moves on its own. **minSdk 31** keeps location and theming code simple, at the cost of reach.

### Limitations and potential improvements

- **Map usability:** Hundreds of standard markers can crowd the world view and are less accessible
  than list rows. Add clustering, selected-marker highlighting, and a way to locate a selected list
  event on the map.
- **Presentation:** Add magnitude-based severity indicators and a custom launcher icon.
- **Refresh and browsing:** Refresh occurs on launch or by user action, not in the background;
  WorkManager could support periodic updates. Relative timestamps do not update continuously.
  Events appear newest first, with no user-configurable filtering or sorting; the feed is fixed
  to M2.5+ earthquakes from the past seven days.
- **Performance and localization:** Feed mapping and distance calculations currently run on the
  main thread; moving them to injected dispatchers would help with larger datasets. Distances are
  straight-line miles only, and strings are English only.
- **Testing and delivery:** Add instrumented Room tests, Compose UI tests for selection and
  permissions, and CI for builds, unit tests, and lint.
