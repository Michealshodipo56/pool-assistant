# Verification and accuracy status

## Automated verification completed on this PC

Command:

```powershell
C:\msys64\ucrt64\bin\g++.exe -std=c++20 -Wall -Wextra -Werror -I .\app\src\main\cpp .\app\src\test\cpp\pool_engine_test.cpp .\app\src\main\cpp\pool_engine.cpp -o .\build\native-tests\pool_engine_test.exe
.\build\native-tests\pool_engine_test.exe
```

Result: `pool_engine_test: PASS` on 2026-10-10.

Covered scenarios: direct equal-mass collision, cushion rebound direction, three-ball collision chain, and pocket entry.

