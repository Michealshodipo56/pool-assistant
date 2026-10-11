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

