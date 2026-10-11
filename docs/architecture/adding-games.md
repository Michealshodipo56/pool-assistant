# Adding another pool game

1. Implement `GameProfile` with a stable ID, display name, and conversion from the generic calibration profile to `PhysicsConfig`.
2. Add a vision configuration for the game's cloth color ranges, table geometry, ball-size constraints, UI exclusion zones, and cue representation. Do not reuse 8 Ball Pool thresholds without fixtures.
3. Add consented fixture frames and ground-truth JSON under `testing/fixtures` for each supported resolution/orientation.
4. Add a profile selector to settings and resolve the profile in `PredictionCoordinator` rather than referencing `EightBallPoolProfile` directly.
5. Run direct, cushion, obstruction, rotation, missing-cue, and multi-ball evaluation sets. Keep results separate by game/profile version.
6. Mark every parameter as observed, estimated, assumed, or unknown. A profile becomes `validated` only after recorded-shot evaluation on a physical device.

The simulator uses capture/table coordinates and has no Miniclip dependency. Game-specific code must remain outside the native engine.

