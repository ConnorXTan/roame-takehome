# Submission notes

Build, run and test instructions are in [README.md](README.md).

## Approach

The two apps are deliberately thin. Everything with a rule in it lives once in `shared/`, and each
native app renders one state object and sends actions back.

```
SwiftUI / Compose           render ExplorerState, call controller methods
ExplorerController          the single state holder: query, results, selection, saved, messages
FlightRepository            refresh logic; the network reaches the UI only after it has been stored
FlightApi / FlightStore     Ktor, SQLDelight (both behind interfaces, so failures can be injected)
domain/                     pure functions: search, aggregation, marker layout, routes, formatting
```

Key decisions:

- **One state holder in shared code, not just a repository.** Selection rules are as easy to get
  wrong as filtering, and implementing them twice is how the two apps would drift apart. Even
  "what should this screen show" is decided once, as a `ContentKind`.
- **Latest input wins.** Filtering runs off the main thread through `mapLatest`, so a slow pass can
  never overwrite a newer one. The query itself updates synchronously, so chips and fields don't lag.
- **Saved state is only ever read back from the database.** Nothing sets it optimistically, so a write
  that failed can't look like it worked. The button waits, and failures show a message with Retry.
- **A cache row records the fetch.** `cache_meta` is what separates "we fetched and got nothing" from
  "we never fetched", which is what keeps an empty response distinct from a failure, even after a relaunch.
- **Refreshes are one transaction.** A bad response or a failed write leaves the previous data intact.
- **Saved flights keep a full copy.** They don't depend on the cache, so they outlive the API.
- **SQLDelight over Room.** Compile-checked SQL with a small runtime. The upsert is written as
  `INSERT OR IGNORE` plus `UPDATE`, because `ON CONFLICT DO UPDATE` needs SQLite 3.24 and Android at
  `minSdk 23` ships older.
- **MapLibre on Android, so no API key is needed to run the project.** Its style ships in the APK and
  markers are drawn as bitmaps, so both still work with no network. iOS uses MapKit.
- **The local API is a Gradle module,** because a JDK is already required, and it adds failure modes
  (`-Pmode=empty|error|malformed|slow`) that made those states testable on a real device.

Three things the fixture forced:

- **364 of the 1,000 flights have a longitude gap over 180°.** Drawn naively, those routes cross the
  wrong way around the world. Android gets unwrapped great-circle paths from shared code; iOS uses
  geodesic polylines. Camera framing uses a centre and span, because a bounding box can't describe them.
- **HND and NRT are both "Tokyo",** so markers group by airport code, never by city.
- **Ten European airports overlap at world zoom.** Clustering would break "group by destination
  airport", and hiding labels would lose their numbers. `layoutMarkers` nudges labels apart for the
  current zoom and draws a leader line back to each airport, so every marker keeps its count and price
  at every zoom. A test runs it against the real fixture.

One thing the starter didn't mention: **with `targetSdk = 37`, Android blocks apps from private
addresses unless they hold `ACCESS_LOCAL_NETWORK`,** and that includes the emulator's `10.0.2.2`. It
fails as a silent connect timeout, so it looks exactly like a dead server. The app requests the
permission before its first fetch, and explains itself if denied.

## Assumptions and tradeoffs

- **Cabin is single choice** ("Any" or one cabin). The brief only asks for multiple selection on loyalty programs.
- **Search is a case-insensitive substring match** on the code or city, so "york" finds New York.
- **Reset clears search as well as filters,** and search terms appear as chips alongside them.
- **The full date range comes from the data,** not hard-coded dates. Controls pushed back to the
  edge of the data's range count as no filter, so they don't show a chip.
- **Results are ordered soonest first, then cheapest,** as the brief doesn't specify an order.
- **Moving the start date past the end drags the end along** (and vice versa), so a range can't invert.
- **A refresh updates the saved copy of any flight still in the response,** so the same flight never
  shows two prices. Flights missing from the response keep their last known details.
- **Saving again keeps a flight's place** in the Saved list rather than moving it to the top.
- **The route connector appears in two forms:** schematic on every card, and a real great-circle arc
  on the map. In the Saved tab, selecting a flight shows the card only.
- **Saved has its own selection,** separate from discovery, because a saved flight may not be in the
  filtered results at all.
- **A successful empty response replaces the cache.** That is what "last successful results" means.
- **One malformed record fails the whole response** rather than showing partial data.
- **Refresh happens on launch, on pull to refresh, and on Retry,** not on return to the foreground.
- **Text formatting is shared and en-US only,** which keeps both apps identical, at the cost of localization.
- **Phones are portrait only.** Landscape left no usable height for results. Tablets still rotate.

## Known limitations

- **Neither app has been run on a physical device.** All checks were on a simulator and an emulator.
- **Smooth scrolling on Android is not proven.** The emulator here uses a software renderer. Frame
  times were around 32ms at the median, but Android's own Settings app was no better under the same
  test, so the renderer is the bottleneck and real hardware still needs measuring.
- **MapKit can't show the whole world on a portrait iPhone.** It stops at about 120° of longitude, so
  iOS starts on Europe to Asia, with fewer airports in view than Android. Panning reaches the rest,
  and when results narrow, the camera moves to frame them, so a search never looks empty.
- **Displaced labels move when zoom settles,** not continuously during a pinch.
- **Overlapping labels can still sit a long way from their airport at world zoom.** Zooming in
  releases them, and the leader lines show which is which.
- **Recreating `MapView` costs about 4 MB of native memory each time on the emulator,** and it isn't
  returned. It's unclear whether that is MapLibre or the emulator's GL driver, and it needs checking
  on real hardware. Rotation no longer recreates the map and measured flat over 24 turns. A theme
  change still does, and so does reopening the activity.
- **Android UI checks are not automated.** iOS has an XCUITest suite; Android was driven by scripted
  adb sessions, which aren't in the repo.
- **Map tiles need a network connection** and come from OpenFreeMap, a free public service with no
  uptime guarantee. A production app would want its own tile provider.
- **There are no database migrations yet.** The schema is at its first version.

## Checks performed

Environment: Xcode 27.2, iOS 26.5 simulator (iPhone 17 Pro), Android 37 emulator (Pixel 7 profile,
software rendering), Apple M5. Built from the command line; the project was not opened in Android Studio.

### Automated

| Suite | Result |
| --- | --- |
| Shared logic on the JVM, including the real 1,000-flight fixture | 147 passing |
| Shared logic on the iOS simulator, on native SQLite | 138 passing |
| iOS UI tests against live servers, including every failure mode | 19 passing |
| Local API | 3 passing |

To check that the tests can actually fail, 25 deliberate bugs were introduced into the shared code one
at a time, such as exclusive date boundaries, grouping markers by city, removing the refresh
transaction, and letting a dropped selection return. **The suite caught all 25.** One of those tests
first passed against broken code and had to be rewritten.

Result counts on both platforms were compared with counts computed separately from the raw fixture in
Python: `jfk` gives 113, `jfk` to `tokyo` gives 7, Business with Aeroplan or Avios gives 140, and
Feb 3 to Feb 5 gives 41.

### On each platform

| Check | iOS | Android |
| --- | :-: | :-: |
| Search ignores case and surrounding spaces | ✓ | ✓ |
| Filters combine, chips remove one, Reset clears all | ✓ | ✓ |
| Date range is inclusive, in Los Angeles and Auckland time zones | ✓ | ✓ |
| Selection survives list to map and back | ✓ | ✓ |
| Selection clears when filtered out, and doesn't return | ✓ | ✓ |
| Marker shows count and lowest price, and opens its flights | ✓ | ✓ |
| Pan and zoom | ✓ | ✓ |
| Vancouver to Sydney crosses the Pacific | ✓ | ✓ |
| A search made before opening the map is framed | ✓ | ✓ |
| Saved flights ignore discovery filters | ✓ | ✓ |
| Reopen with the API stopped: results, stale banner, search, remove | ✓ | ✓ |
| No cache and no API: error, and Retry recovers | ✓ | ✓ |
| Successful empty response differs from a failure | ✓ | ✓ |
| HTTP 500 and malformed JSON, with and without a cache | ✓ | ✓ |
| Saved flight survives being dropped by the API | ✓ | ✓ |
| Failed save stays unsaved and says so | ✓ | ✓ |
| Dark mode | ✓ | ✓ |
| Local network permission denied, then granted in Settings | n/a | ✓ |

### Performance and memory

| Measurement | Result |
| --- | --- |
| iOS, main-thread cost of applying 1,000 results (debug build) | 8ms median, 10ms p95, 20ms max |
| iOS, 5-minute stress run | memory steady around 260 MB, `leaks` reports 0 |
| Android, cold start (release, emulator) | 715ms |
| Android, 30 rounds of map, list and search | Java heap flat, total memory steady around 122 MB |
| Android, 24 rotations with the map open | native heap flat at 48 MB (was growing 4.4 MB per rotation before the fix) |
| Android, scrolling 1,000 rows (release, software renderer) | 32ms median frame; Settings app measured 30ms, with a worse p90 |

### Bugs these checks found

Fixed along the way, listed because they show what the tests above were worth:

- Tapping an empty part of the Android Saved tab could save an invisible flight, because Explore stays
  composed underneath and transparency doesn't stop input. Dragging there panned the hidden map too.
- Typing quickly on Android dropped characters, because the field compared itself to state a frame old.
- Android was blocked from the API entirely, by the local network rule described above.
- An open destination stayed highlighted beside a different flight's route, and reopened on close.
- One failed read ended saved-flight updates for good, so later saves committed but never appeared.
- Leader lines for labels pushed over the antimeridian crossed the entire map.
- A search could leave its only markers off screen, so the map looked empty.
- After changing filters, the list stayed mid-way through the new results.
- SQLite was opened on the main thread at launch on iOS.
- `DEBUG` wasn't defined in the iOS project, so the debug-only switch was silently compiled out.
- The starter's `gradlew` wasn't executable and its scheme file had the wrong extension, both fixed.

## How AI was used

Claude Code wrote the code, tests and these notes, working from the brief.

Its output was checked by running it, not by trusting it:

- Everything was exercised in the real apps against a real server, with screenshots reviewed at each
  step. Most of the bugs above came out of that.
- Claims were tested against something independent of the code that made them: fixture counts computed
  separately, deliberate mutations, stopping the actual server, and a control app for performance.
- Failures were traced to a cause before being fixed, and any fix for shared logic got a test that
  failed first.
- A separate agent reviewed the finished code against the brief, without being told what was already
  known. It reported 11 issues. All were checked, and the confirmed ones were fixed, including the
  Saved tab bug above, which an earlier test had wrongly cleared because it tapped between two rows.
