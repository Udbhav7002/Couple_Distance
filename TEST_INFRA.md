# E2E Test Infra: Couple Distance (Closer)

## Test Philosophy
- Opaque-box, requirement-driven verification covering pairing, live coordinate sync, distance computation, widget layouts, and UI styling.
- Methodology: Category-Partition + Boundary Value Analysis + Pairwise Combinatorial + Real-World Workloads.

## Feature Inventory & Test Coverage
| # | Feature | Requirement | Tier 1 (Isolated) | Tier 2 (Boundary) | Tier 3 (Cross-Feature) | Tier 4 (E2E Scenarios) |
|---|---------|-------------|:-----------------:|:-----------------:|:----------------------:|:----------------------:|
| 1 | Live Partner Pairing | R1 | Pair with 6-char code, non-mock names ("Dev", "Nandhini") | Invalid code, expired session, re-pair | Pairing + coordinate push | Two-device full pairing flow |
| 2 | Real-Time Sync | R1 | Coordinates, battery, sharing status sync across clients | Zero battery, sudden disconnect, reconnect | Sync during active location movement | Dual-device live session maintenance |
| 3 | Eliminate Mock Fallback | R1, R2 | No fallback to "Ava", no 4.2 km override in Refresher | First fix > 50km from previous point | Movement crossing 50km boundary | Real devices separated by real distances |
| 4 | High-Accuracy Location | R2 | PRIORITY_HIGH_ACCURACY, sub-second to 5s interval, low displacement | Min distance 0m, GPS jitter handling | Location stream during live mode | Realistic walking/movement traces |
| 5 | Co-Location Distance | R2 | Exact Haversine math, sub-10m reporting, cm/m formatting | Exactly 0m, 0.5m, 9.9m, 10.1m | Co-location + widget status update | Two co-located devices (< 10m) |
| 6 | Widget Sizing Options | R3 | 1x2, 2x3, 1x3, 4x3 provider registrations and cell spans | Min/max resize bounds | Sizing changes + live distance updates | Home screen widget deployment across sizes |
| 7 | Widget Layouts & Status | R3 | Vertical stacked layout for 1x2/1x3, horizontal for 2x3/4x3 | Long names, offline/stale status | Live partner distance in widget | Widget background update on partner movement |
| 8 | Matte UI Theming | R4 | Solid matte surfaces, no pulsing radial blur orbs, solid buttons | Contrast checks, dark surfaces | UI state during active connection | App browsing under matte theme |
| 9 | Launcher Logo Centering | R4 | Symmetrical lens coords $(54, 54)$, connected bridge, circle radius $\le 36$ | Circular crop bounds verification | Adaptive icon rendering | Launcher home icon display |

## Test Architecture
- Test Runner: `./gradlew test` and dedicated automated integration test classes.
- Integration Test Harness: Simulates two independent clients (`Client A = Dev`, `Client B = Nandhini`) connecting over the live backend, exchanging coordinates at various distances (co-located 2m, walking 25m, separated 5km), and verifying state consistency and distance math.
- Pass/Fail semantics: All unit and integration test assertions must pass with exit code 0.
