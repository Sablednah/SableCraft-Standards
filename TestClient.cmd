@echo off
rem ---------------------------------------------------------------------
rem TestClient.cmd - launch a dev client for two-player testing.
rem
rem   TestClient.cmd            -> TestBuddy   (the second player)
rem   TestClient.cmd main       -> Sablednah   (you)
rem
rem Double-click for TestBuddy, or from PowerShell: .\TestClient.cmd main
rem
rem WHY THIS EXISTS, twice over:
rem  1. `./gradlew runClientBuddy` from WSL launches through WSLg and the
rem     window often never appears. Running from WINDOWS renders natively.
rem     (Same workaround as LegendQuest's TestClient.cmd.)
rem  2. Your real CurseForge instance cannot join the dev server - it has
rem     LegendQuest, ZombieMod, CityWorld and the FTB mods, and NeoForge
rem     refuses when the required-mod lists disagree ("bad network
rem     protocol"). A dev client matches the dev server exactly.
rem
rem Pair with the dev server, started from WSL:  ./gradlew runServer
rem Both clients auto-connect to 127.0.0.1:25569.
rem ---------------------------------------------------------------------
setlocal
cd /d "%~dp0"

set "TASK=runClientBuddy"
set "WHO=TestBuddy"
if /i "%~1"=="main" set "TASK=runClientMain" & set "WHO=Sablednah"

set "JAVA_HOME=%USERPROFILE%\curseforge\minecraft\Install\runtime\java-runtime-delta\windows-x64\java-runtime-delta"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo Could not find CurseForge's JDK 21 at:
    echo   %JAVA_HOME%
    echo Edit JAVA_HOME in this file to point at any JDK 21.
    pause
    exit /b 1
)

rem A separate project cache AND build directory per client. Both are needed: the cache stops
rem the gradle daemons fighting over lock files, and -PwinClient gives each client its own
rem build/ so it never tries to delete the neoforge jar the running WSL server holds open.
echo Starting %WHO% (first run compiles - be patient)...
call gradlew.bat %TASK% --project-cache-dir .gradle-win-%WHO% -PwinClient=%WHO%
pause
