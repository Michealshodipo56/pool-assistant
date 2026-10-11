# Architecture

## Runtime data flow

`MainActivity` obtains overlay permission and a fresh MediaProjection consent result. `AssistantService` immediately becomes a typed foreground service, owns the capture session, and creates three independent windows:

1. a full-screen non-focusable, non-touchable trajectory canvas;
2. a small draggable bubble;
3. a compact menu created only while open.

`ScreenCaptureController` acquires at most two RGBA buffers and calls `acquireLatestImage()`. It throttles input to roughly 12.5 observations/second and submits to a conflated channel, so old gameplay frames cannot build a queue.

`OpenCvVisionEngine` segments the cloth, detects circles, identifies the most plausible low-saturation/high-value cue ball, finds a nearby cue/guide line, and assigns stable IDs by gated nearest-neighbour association. An absent cue direction produces no simulation.

`PredictionCoordinator` reads the current DataStore settings, creates typed ball states, scales the assumed speed and friction into capture pixels, invokes JNI off the UI thread, and publishes immutable state flows. `TrajectoryOverlayView` maps capture coordinates to overlay coordinates and invalidates stale observations after 700 ms.

## Boundaries

- `capture`: Android MediaProjection and image-buffer lifecycle only.
- `vision`: image analysis, temporal tracking, and coordinate transforms.
- `physics`: game-independent models, JNI codec, and native simulator.
- `game`: profile-specific mapping from calibration to engine configuration.
- `calibration`: estimation math; persistence belongs to `settings`.
- `overlay`: service/window lifecycle and Canvas rendering.
- `pipeline`: orchestration, newest-frame policy, and telemetry.

The source is one Android module to keep the initial build small, but package dependencies follow these boundaries. They can become Gradle modules without changing public models.

## Physics model

The engine uses fixed-step semi-implicit motion in 2D. Balls are equal-mass disks. Collision impulses act along the contact normal and use configurable restitution; tangential impulse, throw, swerve, cloth anisotropy, and spin are not modeled. Rolling loss is constant deceleration. Cushions reflect the normal velocity component with a configurable restitution. Pockets trigger within 1.75 ball radii of configured pocket centers.

The fixed step is 1/180 s, bounded to 50,000 steps, eight seconds, and 128 events by default. Penetration is symmetrically corrected. These safeguards prevent unbounded work but do not make near-simultaneous contact exact.

Basic mode draws cue motion and the first object ball; when that object reaches a secondary ball, the branch stops at the impact. All Lines resolves and records all impacts until rest, pocket, time limit, or event limit.

## Uncertainty

Shot speed is not observable from a static frame. The UI exposes an explicitly assumed shot speed. Spin is not estimated and is omitted. Unvalidated calibration reduces displayed confidence; incomplete simulations and low-confidence segments render dashed amber. The renderer shows nothing when the cue direction is absent or stale.

## Security and privacy

Captured pixels live only in memory and are recycled after analysis. There is no network permission, frame persistence, Accessibility service, game-process access, input injection, or remote backend.

