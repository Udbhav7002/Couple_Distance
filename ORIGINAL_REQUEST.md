# Original User Request

## Initial Request — 2026-11-13T10:04:29Z

Make it 10 or 20 agents, make them to work on everything, and if you want, try to test it twice or thrice by using multiple agents. Fix the live partner pairing and location accuracy for the couple distance app so Dev and Nandhini can connect across devices without falling back to a demo partner, compute accurate distances when physically close, provide multiple widget sizing options (1x2, 2x3, 1x3, 4x3), and update the UI with a matte aesthetic and centered logo.

Working directory: /Users/sanagariudbhav/Udbhav/Projects/Couple_Distance
Integrity mode: development

## Requirements

### R1. Live Partner Pairing and Real-Time Backend
Implement a live backend and pairing system so two devices can pair via connection code under real user names (Dev and Nandhini) without falling back to a demo partner or mock backend. Both devices must establish and maintain an active synchronized session over the network.

### R2. High-Accuracy Location Tracking and Distance Computation
Track live GPS locations for both paired devices using high-accuracy location services and update their distance in real time. When both devices are co-located, the computed distance must accurately reflect their physical proximity (sub-meter / co-located accuracy) rather than a fixed 4.2 km mock distance.

### R3. Android Home Screen Widget Sizing Options
Provide Android home screen widget support with selectable sizing options for 1x2, 2x3, 1x3, and 4x3 dimensions, displaying partner distance and connection status.

### R4. Matte UI Styling and Logo Alignment
Update the app UI with a clean, matte aesthetic. Align the app launcher logo so the outer circular frame and spectacles icon are properly centered and proportioned.

## Acceptance Criteria

### Pairing & Backend
- [ ] Entering names and connection code on two distinct devices establishes a real paired session without showing demo partner data or placeholder names.
- [ ] Real-time backend synchronizes state between paired devices across the network.

### Location & Distance
- [ ] When two devices are co-located, the computed distance reflects physical proximity (< 10 meters) instead of the 4.2 km mock offset.
- [ ] Location updates from either device update the displayed distance on both devices in real time.

### Widgets & UI
- [ ] Android home screen widgets support 1x2, 2x3, 1x3, and 4x3 dimensions.
- [ ] Widgets display accurate live distance and active connection status.
- [ ] App UI adheres to a matte visual theme.
- [ ] App logo displays the spectacles icon centered within the circular boundary.

### Verification
- [ ] Automated integration tests simulate two independent clients pairing and exchanging live coordinates end-to-end.
- [ ] All automated tests pass without errors.
