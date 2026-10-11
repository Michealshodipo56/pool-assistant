# 8 Ball Pool APK analysis

Analysis date: 2026-10-10. Source: user-supplied `8 Ball Pool.apk`.

SHA-256: `D6C94873C7F8C8FF948DC72854BEDAD6B85E5E66E0ACD04E0A85EC6DCD2DF45B`

Only static ZIP/resource inspection was performed. No game was launched, modified, instrumented, decrypted, or attached to. No anti-tamper or integrity control was bypassed.

## Verified findings

| Finding | Evidence | Classification | Implication |
|---|---|---|---|
| APK has 5,769 ZIP entries | Archive directory listing | Direct observation | This is a large base package, not a small reference fixture. |
| Built with Android Gradle Plugin 8.7.0 | `META-INF/com/android/build/gradle/app-metadata.properties` | Direct observation | Build tooling only; says nothing about game physics. |
| Contains 20 DEX files | `classes.dex` through `classes20.dex` | Direct observation | Much of the Android/ad/application layer is bytecode. It was not decompiled. |
| Contains arm64-v8a and armeabi-v7a payload paths | `assets/a0r.../a897.../<abi>/` | Direct observation | The supplied base APK carries two Android ABI variants. |
| Gameplay-named payload exists | `libgame-BPM-GooglePlay-Gold-Release-Module-4039.so` under both ABI asset paths | Direct observation | Native gameplay code appears to be delivered through an asset loader. It was not reverse-engineered. |
| Gameplay payload is stored/opaque in the archive | arm64 entry 71,081,833 bytes; armeabi-v7a entry 41,160,111 bytes; compressed size equals stored size | Direct observation | No constants are claimed from this protected/opaque module. |
| Ball art exists for numbered balls and multiple density/device suffixes | `assets/unpack/ball0...ball15` with `-hd`, `-ipad`, `-ipadhd` variants | Direct observation | Useful for visual recognition research, not physical radius. A `.png`-named sample is actually WebP (`RIFF...WEBP`). |
| Cue/table/tutorial UI resources exist | cue collections/stats, `Table_Begginer_Cue*`, `CushionShotTutorial.*`, many `.ccbi`, plist, atlas and skeleton files | Direct observation | Confirms visual resources and tutorial UI; does not establish collision equations. |
| APK is signed | `META-INF/BNDLTOOL.RSA` | Direct observation | The research copy is a signed package; it is not modified or redistributed by this project. |

The binary AndroidManifest could not be decoded in this environment because `aapt2`, `apkanalyzer`, and JADX are absent. Consequently, package/version/minSdk/targetSdk/component metadata is **unknown from this inspection** and must not be guessed. The archive does contain standard AndroidX and advertising library metadata.

## Physics status

| Parameter | Status | Application handling |
|---|---|---|
| Playable table bounds | Assumption requiring per-device calibration | Automatic green/blue cloth contour with saved normalized manual fallback. |
| Ball radius | Assumption requiring calibration | Hough radius bounded around saved short-side fraction. |
| Pocket centers/geometry | Estimated from detected table rectangle | Six standard centers; capture evidence must validate offsets and mouth radii. |
| Ball-ball restitution | Unknown | Independent default 0.96; configurable in engine but not claimed as a game constant. |
| Cushion restitution | Unknown / experimentally estimable | Default 0.82; calibration UI plus observed incoming/outgoing speed estimator. |
| Rolling resistance / velocity decay | Unknown / experimentally estimable | Constant-deceleration model; track regression estimator rejects high residuals. |
| Cue power mapping | Unknown | Clearly labeled assumed shot speed; no fixed-frame inference. |
| Spin, swerve, throw | Unknown | Not modeled; lowers real-shot accuracy and must be called out during tests. |
| Collision tolerance | Unknown | Independent engine uses overlap correction and 1/180 s fixed step. |
| Existing guide geometry | Visually present in gameplay/tutorial resources, numeric behavior unknown | The assistant estimates visible line/cue direction; it does not import game logic. |

No recovered value is presented as an internal Miniclip physics constant. Defaults belong to the independent simulator and remain unvalidated until physical-device calibration.

## Legal/distribution implication

The helper is process-independent and requests normal Android overlay and screen-share consent. Distribution still requires a current review of Google Play policies and the game's terms. The supplied APK is not included in the app build and none of its assets are copied into this project.

