@echo off
rem ---------------------------------------------------------------------
rem TestClient.cmd - launch a dev client for two-player testing.
rem
rem   TestClient.cmd            -> TestBuddy   (the second player)
rem   TestClient.cmd main       -> Sablednah   (you)
rem   TestClient.cmd third      -> TestThird   (a bystander, for /socialspy and friends)
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

rem Parenthesised, and one command per line. `if cond set A=1 & set B=2` does NOT do what it
rem reads like: cmd.exe splits on `&` before evaluating the if, so everything after it runs
rem unconditionally. The one-line form left WHO as TestThird whatever you typed, while TASK
rem stayed on TestBuddy's - so the default launch ran the buddy client in the third client's
rem directory, and `third` never reached its own task at all.
set "TASK=runClientBuddy"
set "WHO=TestBuddy"
if /i "%~1"=="main" (
    set "TASK=runClientMain"
    set "WHO=Sablednah"
)
if /i "%~1"=="third" (
    set "TASK=runClientThird"
    set "WHO=TestThird"
)

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
rem ---------------------------------------------------------------------
rem Mute every sound category before launching.
rem
rem Two dev clients and a server on one machine means the same sound plays
rem two or three times, slightly out of step, for hours. It is genuinely
rem unpleasant to test through, and remembering to turn it down in-game is
rem a thing you remember on the second launch, never the first.
rem
rem Done here rather than by hand because a fresh client writes a default
rem options.txt the first time it starts - so a run directory that does not
rem exist yet is exactly the one that would come up loud.
rem ---------------------------------------------------------------------
rem Must match gameDirectory in build.gradle for each run config, or the mute below writes
rem options.txt into a directory the client never opens.
set "RUNDIR=runBuddy"
if /i "%WHO%"=="Sablednah" set "RUNDIR=runMain"
if /i "%WHO%"=="TestThird" set "RUNDIR=runThird"
powershell -NoProfile -Command ^
  "$d = '%RUNDIR%'; $f = Join-Path $d 'options.txt';" ^
  "New-Item -ItemType Directory -Force -Path $d | Out-Null;" ^
  "$cats = 'master','music','record','weather','block','hostile','neutral','player','ambient','voice','ui';" ^
  "$lines = if (Test-Path $f) { Get-Content $f } else { @() };" ^
  "$lines = $lines | Where-Object { $_ -notmatch '^soundCategory_' };" ^
  "$lines += $cats | ForEach-Object { 'soundCategory_' + $_ + ':0.0' };" ^
  "Set-Content -Path $f -Value $lines"

echo Starting %WHO% (first run compiles - be patient)...
call gradlew.bat %TASK% --project-cache-dir .gradle-win-%WHO% -PwinClient=%WHO%
pause
