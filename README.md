# Roame starter

SwiftUI and Compose display **Hello world!** from the shared KMP `Greeting.message()`.

- **Android:** Open this folder in Android Studio Quail 3 or newer. Install Android SDK 37.0 / build-tools 37.0.0 if prompted; run `androidApp` on an emulator.
- **iOS:** On a Mac with Xcode 26.4 or newer, open `iosApp/RoameStarter.xcodeproj`, select `RoameStarter` and an iPhone simulator, then Run. Install Android Studio or a JDK first; Xcode builds KMP automatically.

Gradle automatically selects or downloads JDK 25. First builds require internet access. On first opening Xcode, build once (⌘B) to generate `Shared`; an initial “No such module 'Shared'” editor error should clear after the build.

Kotlin 2.4.21 · Gradle 9.7.0 · AGP 9.3.1 · Compose BOM 2026.09.00 · Activity 1.13.0 · Swift 6.

The Gradle wrapper's upstream license is included in `LICENSE`.

Flight data is provided in `fixtures/flights.json`.
