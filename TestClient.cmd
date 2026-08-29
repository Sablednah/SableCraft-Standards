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
rem Clients auto-connect to the port in gradle.properties (dev_server_port),
rem which differs per branch so two Minecraft lines can run side by side.
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

rem ---------------------------------------------------------------------
rem Run Gradle on a real JDK, and let it fetch the rest.
rem
rem The trap here cost an hour. CurseForge ships two Java runtimes and they
rem are NOT the same kind of thing: java-runtime-delta (21) is a full JDK,
rem while java-runtime-epsilon (25) is a JRE. Pointing JAVA_HOME at epsilon
rem for a 26.x build looks right and fails deep inside NeoForm with
rem   NullPointerException: ... because "compiler" is null
rem - which names no Java version and reads like a corrupt cache. It is
rem ToolProvider.getSystemJavaCompiler() returning null, because a JRE has
rem no javac to decompile-and-recompile Minecraft with.
rem
rem So Gradle always runs on the JDK 21, on every branch. It is only the
rem launcher; the Java 25 that 26.x actually compiles against is
rem auto-provisioned by the foojay resolver in settings.gradle. That is why
rem there is no version switch here - the toolchain already knows, from
rem gradle.properties, and one JDK that can bootstrap is all this needs.
rem ---------------------------------------------------------------------
set "JAVA_HOME=%USERPROFILE%\curseforge\minecraft\Install\runtime\java-runtime-delta\windows-x64\java-runtime-delta"
if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo Could not find a JDK - note a JDK, not a JRE - at:
    echo   %JAVA_HOME%
    echo.
    echo CurseForge installs this one with any 1.21.x instance. If you have
    echo removed those, point JAVA_HOME in this file at any JDK 21 or newer
    echo that has bin\javac.exe. Gradle downloads whatever else it needs.
    pause
    exit /b 1
)

rem Read the Minecraft line out of gradle.properties, for the mods sync below
rem and so the echo says which line you are about to launch.
for /f "tokens=2 delims==" %%v in ('findstr /b "minecraft_version=" gradle.properties') do set "MCVER=%%v"
echo Minecraft %MCVER%, Gradle on "%JAVA_HOME%"

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

rem ---------------------------------------------------------------------
rem Mirror the dev server's mods folder into this client's.
rem
rem NeoForge refuses a connection when the required-mod lists disagree, and
rem says only "bad network protocol" - it does not name the mod. The dev
rem server carries LuckPerms, CityWorld, LegendQuest, MobHealth and
rem ZombieMod alongside Standards and Factions from source, so a client
rem with an empty mods folder is turned away at the door.
rem
rem Synced on every launch rather than once, because the mismatch appears
rem the moment a jar is added on either side - which is exactly when you
rem are thinking about something else.
rem ---------------------------------------------------------------------
rem Two branch shapes to cover: 26.x gives the server a per-version game
rem directory (run-mc26.1.2/) so two Minecraft lines can hold worlds side by
rem side, while 1.21.11 predates that and still uses plain run/. Falling back
rem rather than assuming matters because the failure is SILENT - a sync that
rem finds no source folder leaves the previous line's jars sitting in the
rem client, which is the mismatch this is here to prevent.
set "SERVERMODS=run-mc%MCVER%\mods"
if not exist "%SERVERMODS%" set "SERVERMODS=run\mods"
if exist "%SERVERMODS%" (
    echo Syncing mods from %SERVERMODS% to %RUNDIR%\mods ...
    robocopy "%SERVERMODS%" "%RUNDIR%\mods" *.jar /MIR /NJH /NJS /NDL /NP /NFL >nul
    rem robocopy returns 0-7 for success. Anything 8+ is a real failure.
    if errorlevel 8 (
        echo WARNING: could not sync mods - %WHO% may be refused with "bad network protocol".
    )
)

echo Starting %WHO% (first run compiles - be patient)...
call gradlew.bat %TASK% --project-cache-dir .gradle-win-%WHO% -PwinClient=%WHO%
pause
