# Closer 💞

[![Latest Release](https://img.shields.io/github/v/release/Udbhav7002/Couple_Distance?color=%23FF6B81&label=Download%20APK&logo=android)](https://github.com/Udbhav7002/Couple_Distance/releases/latest)

**How far apart are the two of you, right now?**

[**📥 Download Latest APK (v1.1.0)**](https://github.com/Udbhav7002/Couple_Distance/releases/latest/download/Closer-v1.1.0.apk)

A couple's proximity app for Android. Two gradient lenses on your home screen — your initials and
your partner's — joined by a bridge line with the live distance sitting on it like a bead, shaped
like a pair of spectacles. Frosted, translucent, battery-friendly. When you cross within reach
(100/200/500 m — your choice), you both get a gentle nudge to meet up.

---

## Features

- **The spectacles widget** (4×2 translucent, tap anywhere = refresh both of you; footer opens the app)
  - States: normal · **Nearby 💞** (rose glow) · refreshing · stale · paused
  - Distance formatting: `87 cm` under a metre · `420 m` · `3.4 km` · `26 km`
- **Eco mode (default)** — no persistent service: refresh on tap, on app open, every 15 min
  (WorkManager), plus passive location hints. ~zero battery.
- **Live mode (toggle)** — a quiet foreground service keeps your side fresh while you move
  (≥50 m or 90 s), so your partner's widget is always current.
- **Nearby alerts** — edge-triggered notification when you enter the radius (no spam),
  with quiet hours presets (Off / Night 23–7 / Late 22–8).
- **Privacy** — location is visible to *exactly* the two of you (Firestore rules + anonymous
  auth), only the latest point is stored (no history), one-tap pause sharing.
- **Demo mode** — without a Firebase config the app runs against a simulated partner
  ("Ava") who drifts around, controllable from Settings → *Demo · move partner*.

## Tech

Kotlin · Jetpack Compose (Material 3) · RemoteViews widget with runtime-drawn gradient bitmaps ·
FusedLocationProvider · WorkManager · DataStore · Firebase (Auth + Firestore, optional at build time).

```
app/src/main/java/com/closer/app/
  ui/          Onboarding · Home · SettingsSheet · SpectaclesVisual (Compose)
  data/        Models · SettingsStore (DataStore) · SnapshotStore (widget cache)
  backend/     Backend interface · FirebaseBackend · MockBackend (persisted demo)
  location/    LocationEngine · DistanceMath · LiveModeService
  widget/      SpectaclesWidgetProvider · WidgetRenderer · WidgetMapper · WidgetUpdater
  refresh/     Refresher (single update path) · PeriodicWorker
  notify/      Notifier · ProximityChecker (edge-triggered alerts)
```

## Build & run

```bash
./gradlew :app:assembleDebug     # debug APK
./gradlew :app:assembleRelease   # signed release (uses keystore.properties + closer-release.jks)
adb install -r app/build/outputs/apk/release/app-release.apk
```

The emulator used in development: `emulator -avd closer` (Google APIs, arm64). Spoof GPS with
`adb emu geo fix <lng> <lat>`.

## Going live with your partner (5 minutes)

1. Create a free Firebase project → https://console.firebase.google.com → *Add project*.
2. *Add app → Android*, package name **`com.closer.app`**, no SHA needed → download
   **`google-services.json`** → drop it into `app/`.
3. In the console enable:
   - **Authentication → Sign-in method → Anonymous** (used for per-device identity)
   - **Firestore Database → Create database** (production mode is fine; add the rules below)
4. Paste these **Firestore security rules** (only the two members can ever read/write):

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /couples/{coupleId} {
      allow read, update: if request.auth != null
        && request.auth.uid in resource.data.members;
      allow create: if request.auth != null;
      match /members/{uid} {
        allow read: if request.auth != null
          && firestore.get(/databases/$(database)/documents/couples/$(coupleId)).data.members.hasAny([request.auth.uid]);
        allow write: if request.auth != null && request.auth.uid == uid;
      }
    }
  }
}
```

5. Rebuild — Firebase activates automatically (`app/build.gradle.kts` applies the
   google-services plugin the moment the JSON exists) and Demo mode disappears.
6. Both phones install the APK → one of you taps **Create our couple**, shares the 6-character
   code, the other taps **Join**. Add the widget from Settings → *✨ Add the widget to your home screen*.

## Notes

- Release keystore lives in `closer-release.jks` (passwords in `keystore.properties`) — keep both
  out of version control; both are already gitignored. Losing the keystore loses the ability to
  update-install over the existing app.
- iOS later: the Firebase data model and rules port as-is; the widget becomes WidgetKit.
