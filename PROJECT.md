# Project: Couple Distance (Closer)

## Architecture
The application is a native Android app built with Kotlin, Jetpack Compose, Coroutines/Flow, and Android AppWidgets.

- **Presentation Layer**: Jetpack Compose UI with `HomeScreen`, `OnboardingScreen`, and custom theme components in `com.closer.app.ui`.
- **Domain & State Layer**: `AppViewModel` managing state flows for `me`, `couple`, `distance`, `bearing`, and connection status.
- **Backend & Sync Layer**: `Backend` interface (`ensureAuth`, `createCouple`, `joinCouple`, `observeCouple`, `pushLocation`, etc.) implemented by live network backend services.
- **Location & Distance Engine**: `LocationEngine` (FusedLocationProviderClient), `LiveModeService` (foreground service for continuous tracking), and `DistanceMath` (Haversine distance calculation and formatting).
- **Home Screen Widgets**: Native Android `AppWidgetProvider`s, `RemoteViews`, `WidgetRenderer` (dynamic bitmap rendering), and `WidgetUpdater`.
- **Assets & Icons**: Adaptive launcher icons and drawables in `res/drawable`.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | F1_LIVE_BACKEND_PAIRING | Real-time network backend & pairing system for two devices with real user names (Dev and Nandhini) via 6-character connection code without mock fallback | M1 | ORIGINAL_REQUEST §R1 |
| 2 | F2_REALTIME_SYNC | Bidirectional synchronized session over network updating location, battery, and status in real-time | M1 | ORIGINAL_REQUEST §R1 |
| 3 | F3_ELIMINATE_MOCK_FALLBACK | Eliminate hardcoded simulated partner ("Ava") and 4.2 km mock distance seeding (`seedMockIfNeeded`) | M1 | ORIGINAL_REQUEST §R1, R2 |
| 4 | F4_HIGH_ACCURACY_GPS | High-accuracy GPS tracking with fast update interval and minimal displacement threshold | M2 | ORIGINAL_REQUEST §R2 |
| 5 | F5_COLOCATION_DISTANCE | Compute physical proximity (< 10m / sub-meter accuracy) when co-located, without 4.2 km mock distance | M2 | ORIGINAL_REQUEST §R2 |
| 6 | F6_WIDGET_SIZING_OPTIONS | Selectable Android home screen widget sizing options for 1x2, 2x3, 1x3, and 4x3 dimensions | M3 | ORIGINAL_REQUEST §R3 |
| 7 | F7_WIDGET_LAYOUTS_AND_STATUS | Responsive RemoteViews layouts (vertical stacked for 1-column, horizontal for multi-column) displaying partner distance and connection status | M3 | ORIGINAL_REQUEST §R3 |
| 8 | F8_MATTE_UI_THEMING | Clean matte aesthetic replacing frosted glassmorphism, blur orbs, and specular glow strokes | M4 | ORIGINAL_REQUEST §R4 |
| 9 | F9_LAUNCHER_LOGO_ALIGNMENT | Centered launcher spectacles logo and circular adaptive frame alignment in `ic_launcher_foreground.xml` | M4 | ORIGINAL_REQUEST §R4 |
| 10 | F10_E2E_INTEGRATION_TESTS | Automated integration test suite simulating two independent clients pairing, streaming GPS, and verifying sub-10m co-location distance | M5 | ORIGINAL_REQUEST §Verification |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | Live Partner Pairing & Real-Time Backend | Implement live backend relay/service, pairing protocol for Dev & Nandhini, eliminate mock fallback & 4.2 km seeding | none | DONE |
| 2 | High-Accuracy Location & Distance | High-accuracy GPS tracking, low displacement threshold, real-time co-located distance computation (< 10m) | M1 | DONE |
| 3 | Multi-Size Android Widgets | Support 1x2, 2x3, 1x3, and 4x3 widgets with providers, layouts, and live status display | M1, M2 | DONE |
| 4 | Matte UI Styling & Logo Alignment | Matte theme overhaul (remove blur orbs/specular highlights, flat dark surfaces) and center launcher icon vector | none | DONE |
| 5 | E2E Dual-Client Integration & Verification | Multi-agent end-to-end integration testing, adversarial verification, and forensic audit | M1, M2, M3, M4 | DONE |

## Interface Contracts
### Client ↔ Backend (`com.closer.app.backend.Backend`)
```kotlin
interface Backend {
    suspend fun ensureAuth(): String
    suspend fun createCouple(me: MemberInit): CoupleState
    suspend fun joinCouple(code: String, me: MemberInit): CoupleState
    fun observeCouple(coupleId: String, myUid: String): Flow<CoupleState?>
    suspend fun pushLocation(coupleId: String, myUid: String, sample: LocationSample)
    suspend fun setSharing(coupleId: String, myUid: String, enabled: Boolean)
    suspend fun setRadius(coupleId: String, myUid: String, radiusM: Double?)
    suspend fun updateMe(coupleId: String, myUid: String, name: String, colorIndex: Int)
}
```

### Location Service ↔ Distance Math
- `DistanceMath.haversineM(a: GeoPoint, b: GeoPoint): Double` returns exact great-circle distance in meters.
- `DistanceMath.formatDistance(meters: Double): String`:
  - `meters < 1.0` -> `"${(meters * 100).roundToInt()} cm"`
  - `meters < 1000.0` -> `"${meters.roundToInt()} m"`
  - `meters >= 1000.0` -> `"${String.format(Locale.US, "%.1f", meters / 1000.0)} km"`

### Widget Providers ↔ Widget Updater
- Receivers:
  - `SpectaclesWidgetProvider` / `SpectaclesWidgetProvider4x3` (4x3)
  - `SpectaclesWidgetProvider2x3` (2x3)
  - `SpectaclesWidgetProvider1x3` (1x3)
  - `SpectaclesWidgetProvider1x2` (1x2)
- State broadcast: `com.closer.app.WIDGET_REFRESH` triggers update across all active widget IDs.

## Code Layout
- `app/src/main/java/com/closer/app/backend/`: Backend interface, live network backend implementation, and couple state models.
- `app/src/main/java/com/closer/app/location/`: LocationEngine, LiveModeService, DistanceMath.
- `app/src/main/java/com/closer/app/widget/`: AppWidgetProviders, WidgetRenderer, WidgetUpdater, WidgetSnapshot.
- `app/src/main/java/com/closer/app/ui/`: Compose UI screens and components (`HomeScreen`, `OnboardingScreen`, `GlassComponents`, `SpectaclesVisual`).
- `app/src/main/res/`: XML layouts, widget metadata, adaptive icons (`ic_launcher_foreground.xml`), drawable backgrounds.
- `app/src/test/java/com/closer/app/`: Unit and integration test suites.
