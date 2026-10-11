# Universal Pool Aiming Assistant

A native Android application that captures a user-approved screen-sharing session, detects a pool table and balls locally with OpenCV, runs a deterministic C++ physics simulation, and draws touch-through trajectories in a `WindowManager` overlay. The initial game profile is 8 Ball Pool.

## What is implemented

- Jetpack Compose control/settings UI with Hilt and DataStore.
- Android foreground service with the Android 14+ `mediaProjection` service type.
- Explicit MediaProjection consent for each session; capture stops and predictions invalidate when permission is revoked.
- Draggable, edge-snapping bubble with persistent position and compact overlay controls.
- Full-screen `FLAG_NOT_TOUCHABLE` trajectory layer, separate from interactive bubble/menu windows.
- OpenCV HSV table segmentation, Hough-circle ball detection, cue-ball classification, Hough-line cue-direction estimation, temporal smoothing, confidence, and stale-frame invalidation.
- Native C++20 multi-ball physics: rolling deceleration, equal-mass impacts, restitution, cushion rebounds, pockets, stopping thresholds, ordered events, event/time limits, Basic and All Lines policies.
- In-game four-corner table marking; the temporary guide disappears after saving and detection is constrained to the marked table. App-screen calibration remains available.
- Newest-frame-only asynchronous processing and latency/memory telemetry.

This project does **not** automate taps or shots, use Accessibility, modify the game, access its process, bypass protections, upload frames, or claim unmeasured accuracy.

