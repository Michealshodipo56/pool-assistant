# Verification and accuracy status

## Automated verification completed on this PC

Command:

```powershell
C:\msys64\ucrt64\bin\g++.exe -std=c++20 -Wall -Wextra -Werror -I .\app\src\main\cpp .\app\src\test\cpp\pool_engine_test.cpp .\app\src\main\cpp\pool_engine.cpp -o .\build\native-tests\pool_engine_test.exe
.\build\native-tests\pool_engine_test.exe
```

Result: `pool_engine_test: PASS` on 2026-10-10.

Covered scenarios: direct equal-mass collision, cushion rebound direction, three-ball collision chain, and pocket entry.

`assembleDebug` completed successfully using JDK 17, SDK Platform/Build Tools 35, CMake 3.22.1, and NDK 30.0.16248370. It now produces separate ABI APKs plus a universal APK. The ARM64 debug APK used on the itel phone is about 35 MB, compared with about 153 MB for the universal debug APK. The application ID is `com.example.poolassistant`, min SDK 26, target SDK 35.

`testDebugUnitTest` completed successfully: 6 tests, 0 failures, 0 errors, 0 skipped. These cover vector normalization, 0°/90° coordinate transforms, calibration regression, and invalid cushion gain.

`assembleDebugAndroidTest` completed successfully, so the instrumentation test APK compiles and packages. `connectedDebugAndroidTest` has not run.

On an itel A665L (Android API 33), ADB installed the app and instrumentation APKs. The user confirmed screen-capture consent, bubble appearance, menu open/close, landscape scrolling, and line rendering without leaving the game. The user also reported that the trajectories are wildly inaccurate. Screenshot instrumentation tests found the cause: on the brown table the detector labeled a yellow ball as the cue ball and admitted 112 false ball candidates; on the blue screenshot with the menu open it did not find a valid table/balls. The loading-screen false-positive rejection test passed. Candidate filtering and table-mask changes have built successfully but have not yet been installed and tested because wireless ADB disconnected. A historical capture-thread timestamp crash and missing OpenCV initialization were fixed earlier.

## Not yet measured

The supplied gameplay screenshots establish one brown-cloth fixture, but no labeled motion/shot sequence has been measured. Therefore:

- Service restart, permission denial/revocation, backgrounding, orientation, cutout, and clean-shutdown behavior remain pending; installation and two instrumentation tests were completed on the phone.
- Ball/table/cue detection precision and recall are unmeasured beyond the three diagnostic screenshots; two of those diagnostic cases failed before the latest filtering change. The new in-game four-corner table selection and cue-guide scoring still need a device test.
- Basic/All Lines positional error, angular error, and outcome agreement have **0 test cases**.
- Capture, render, and end-to-end performance on a phone are unmeasured.
- There is no accuracy percentage and no frame-rate claim.

Use `testing/benchmarks/accuracy-template.csv`; keep separate rows/aggregates for Basic vs All Lines and direct vs cushion vs multi-ball shots.

## Physical-device test procedure

1. Build/install using the README commands and start Logcat.
2. Test permission denial first: deny overlay, then deny capture. Verify no overlay/capture remains and the UI explains the state.
3. Start again, grant permissions, open 8 Ball Pool, and record phone model, OS, resolution, refresh rate, orientation, and game version.
4. In the floating menu, select Mark Table Corners, tap top-left/top-right/bottom-right/bottom-left inside the cushions, and confirm the guide disappears after the fourth tap. Check Undo/Cancel and re-marking after rotation. Verify bubble drag/snap near all four edges, menu touch isolation, gameplay touches outside the controls, background/foreground, capture-token revocation, and Stop Assistant cleanup.
5. Collect at least 20 consented frames for each targeted resolution/layout: clear rack, sparse table, partial obstruction, absent cue, and visually busy UI. Keep frames local.
6. For accuracy, screen-record repeatable shots. Annotate observed collision points and paths, export predicted events, and fill the CSV. Report median, 90th percentile, maximum, and sample count—not only an average.
7. Run direct, cushion, and multi-ball sets separately for Basic and All Lines. Include weak/strong shots and explicitly exclude or tag visible spin until spin is modeled.
8. Observe the in-app diagnostics plus Android Studio Profiler for 10 minutes. Record thermal throttling, battery drain, memory trend, capture/detection/physics/render/end-to-end latency, and processed fps.

## Production-readiness assessment

Status: **engineering alpha; not production-ready**.

The Android debug APK compiles, but physical behavior has not been verified here. CV thresholds are intentionally conservative and need real fixtures. The independent physics model omits spin/throw and uses uncalibrated defaults. Distribution-policy and game-terms review is also required.

## Remaining user actions

- Install on the target phone and complete permission/lifecycle checks.
- Capture labeled fixtures and calibrate each display/layout.
- Run and publish the accuracy/latency evaluation with actual sample counts.
- Decide whether spin estimation is required based on measured error.
