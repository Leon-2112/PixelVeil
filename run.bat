@echo off
setlocal
cd /d "%~dp0"
if not exist out mkdir out
javac -encoding UTF-8 -d out src\main\java\com\pixelveil\*.java
if errorlevel 1 (
  echo.
  echo Compilation failed. Install JDK 17 or newer and make sure javac is on PATH.
  pause
  exit /b 1
)
java -cp out com.pixelveil.SteganographyApp
if errorlevel 1 (
  echo.
  echo PixelVeil terminated with an error.
  pause
)
endlocal
