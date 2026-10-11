# Universal Pool Aiming Assistant

A native Android application that captures a user-approved screen-sharing session, detects a pool table and balls locally with OpenCV, runs a deterministic C++ physics simulation, and draws touch-through trajectories in a `WindowManager` overlay. The initial game profile is 8 Ball Pool.

## What is implemented

- Jetpack Compose control/settings UI with Hilt and DataStore.
- Android foreground service with the Android 14+ `mediaProjection` service type.
- Explicit MediaProjection consent for each session; capture stops and predictions invalidate when permission is revoked.

