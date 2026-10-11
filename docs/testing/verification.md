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

