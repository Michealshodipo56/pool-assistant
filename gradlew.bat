@rem Minimal Windows launcher. Run `gradle wrapper` once if gradle-wrapper.jar is absent.
@echo off
set DIR=%~dp0
set JAVA_EXE=java.exe
if defined JAVA_HOME set JAVA_EXE=%JAVA_HOME%\bin\java.exe
if not exist "%DIR%gradle\wrapper\gradle-wrapper.jar" (
  echo Missing gradle\wrapper\gradle-wrapper.jar. Open the project in Android Studio or run: gradle wrapper --gradle-version 8.11.1
  exit /b 1
)
"%JAVA_EXE%" -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*

