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

## Build on Windows

1. Install Android Studio from <https://developer.android.com/studio> and select Android SDK Platform 35, SDK Build-Tools 35, Platform-Tools, CMake 3.22.1, and NDK 30.0.16248370 in SDK Manager. Use JDK 17 for this Gradle 8.11.1 build.
2. Open this directory in Android Studio and allow Gradle sync. The Gradle wrapper JAR is included.

3. Set the SDK path if Android Studio has not created it:

   ```powershell
   "sdk.dir=$($env:LOCALAPPDATA -replace '\\','\\')\\Android\\Sdk" | Set-Content .\local.properties
   ```

4. Build and test:

   ```powershell
   $env:JAVA_HOME = (Resolve-Path .\.tooling\jdk17\jdk-17.0.20.1+1).Path
   .\gradlew.bat testDebugUnitTest
   .\gradlew.bat assembleDebug
   ```

   The project-local JDK path above exists in this workspace. In a fresh checkout, point `JAVA_HOME` to any installed JDK 17. Run `.\gradlew.bat connectedDebugAndroidTest` after connecting an Android device.

The phone-specific APK is produced at `app\build\outputs\apk\debug\app-arm64-v8a-debug.apk` for ARM64 phones. A larger `app-universal-debug.apk` is also built for general use.

## Phone setup and installation

1. On the phone, open **Settings → About phone** and tap **Build number** seven times.
2. Open **Developer options**, enable **USB debugging**, connect USB, and approve the computer fingerprint.
3. From PowerShell:

   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r ".\app\build\outputs\apk\debug\app-arm64-v8a-debug.apk"
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -s PoolAssistant AndroidRuntime
   ```

4. Launch the app, tap **Start Assistant**, grant notification/overlay permission, then approve Android's screen-capture dialog. Consent is required again after each stopped session.
5. Open 8 Ball Pool, tap the floating `◎` bubble, choose **Mark Table Corners (in game)**, then tap the inside-cushion corners in this order: top-left, top-right, bottom-right, bottom-left. The guide closes after the fourth corner. Use Cancel/Undo to correct a mistake. The saved area is reused on the same screen layout; mark it again after a layout or resolution change.

## Troubleshooting

- No bubble: confirm **Display over other apps** is enabled for Pool Assistant.

