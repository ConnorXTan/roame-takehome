# Flight Explorer

A flight list and flight map on iOS (SwiftUI) and Android (Jetpack Compose), backed by one Kotlin
Multiplatform layer for networking, persistence, search, filtering, map aggregation and selection.

See [NOTES.md](NOTES.md) for the architecture, assumptions, tradeoffs, limitations, the checks that
were performed, and how AI was used.

```
shared/      Kotlin Multiplatform: API client, SQLite store, search, filters, map logic, state holder
androidApp/  Jetpack Compose app, MapLibre map
iosApp/      SwiftUI app, MapKit map, UI tests
server/      The local API (Ktor)
fixtures/    flights.json, as supplied
scripts/     ios-failure-tests.sh
```

## Requirements

- JDK 17 or newer to start Gradle. Gradle fetches the JDK 25 it runs on by itself.
- Android SDK 37.0 and build-tools 37.0.0. Android Studio will offer these; on the command line,
  put the SDK path in `local.properties` as `sdk.dir=...`.
- Xcode 26.4 or newer, with an iPhone simulator.

First builds need internet access, and map tiles need it at runtime. No API keys are required.

If `java` isn't on your `PATH`, set `JAVA_HOME` first. With Homebrew's JDK:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
```

## Run the API

```sh
./gradlew :server:run
```

It serves `GET /flights` on port 8080, returning `fixtures/flights.json` unchanged. Leave it running
while you use the apps. Use `-Pport=9000` for another port.

To see the failure states, start it in a failure mode:

```sh
./gradlew :server:run -Pmode=empty       # 200 with no flights
./gradlew :server:run -Pmode=error       # HTTP 500
./gradlew :server:run -Pmode=malformed   # 200 with broken JSON
./gradlew :server:run -Pmode=slow        # the fixture, 4 seconds late
```

## Run the apps

### Android

Open the project in Android Studio and run `androidApp` on an emulator, or:

```sh
./gradlew :androidApp:installDebug
```

The first launch asks for "nearby devices". That is Android's local network permission, which the app
needs to reach the API on `10.0.2.2` because the project targets SDK 37. Allow it. The app explains
what to do if you don't.

Judge scrolling on the release build, since Compose is much slower in debug builds. It is signed with
the debug key, so it needs no setup:

```sh
./gradlew :androidApp:installRelease
```

On a physical device, pass your computer's address to a debug build, which allows plain HTTP to any
host: `./gradlew :androidApp:installDebug -PapiBaseUrl=http://192.168.1.20:8080`.

### iOS

Open `iosApp/RoameStarter.xcodeproj`, choose the `RoameStarter` scheme and an iPhone simulator,
and run. Xcode builds the shared framework itself. An initial "No such module 'Shared'" editor error
clears after the first build. On a physical device, set `API_BASE_URL` in the scheme's environment.

## Run the tests

```sh
./gradlew :shared:testAndroidHostTest      # shared logic on the JVM, including the real fixture
./gradlew :shared:iosSimulatorArm64Test    # shared logic on the iOS simulator, on native SQLite
./gradlew :server:test                     # the API
```

The iOS UI tests need the API running (`./gradlew :server:run`), then:

```sh
xcodebuild test -project iosApp/RoameStarter.xcodeproj -scheme RoameStarter \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' CODE_SIGNING_ALLOWED=NO
```

The failure-mode tests skip themselves there. This script starts a server for each failure mode and
runs the whole UI suite, so stop any API on port 8080 first:

```sh
scripts/ios-failure-tests.sh "iPhone 17 Pro"
```

## See a failed save

Saved flights are local, so a real write failure is hard to provoke. Debug builds have a switch that
makes every save and remove fail:

- Android: `adb shell am start -n travel.roame.takehome.android/.MainActivity --ez failSavedWrites true` (after a force stop)
- iOS: set `FAIL_SAVED_WRITES=1` in the scheme's environment

## Offline

Open each app once with the API running, stop the API, then close and reopen the app. Cached results
and saved flights are all there, and a banner shows when the data was last updated. Map tiles aren't
downloaded for offline use.
