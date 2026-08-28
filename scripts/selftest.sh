#!/usr/bin/env bash
# Boot the dev server with -Pselftest and report whether both self-tests passed.
#
#   ./scripts/selftest.sh
#
# The spine of cross-version verification. A Minecraft drop's dangerous failures here are all
# SILENT: a command that stops registering because a requires() predicate now returns false, a
# codec that stops round-tripping so every home becomes a hole in the ground, an economy provider
# that no longer wins so money goes quietly into a second ledger, a permission node that resolves
# false for everyone and makes the mod look broken with no error anywhere. Every one of those is
# already asserted by SelfTest. This is what runs it somewhere other than by hand.
#
# ⚠ Asserts PRESENCE, not exact counts. CityWorld measured its own counts wobbling by one or two
# between identical runs; tightening that into an equality assertion produces a test that fails at
# random and teaches everybody to ignore it.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

MC="$(sed -n 's/^minecraft_version=\(.*\)$/\1/p' gradle.properties)"

# The JDK tracks Minecraft: 26.1 ships java-runtime-epsilon and needs 25. Honour a preset
# JAVA_HOME first, then look through the sibling mods' portable JDKs — no repo here carries one.
if [ -z "${JAVA_HOME:-}" ]; then
    case "$MC" in
        1.*) want=jdk21 ;;
        *)   want=jdk25 ;;
    esac
    for candidate in "$ROOT/tools/$want" "$ROOT/../MobHealth-Forge/tools/$want" \
                     "$ROOT/../CityWorld-ReForged/tools/$want"; do
        if [ -d "$candidate" ]; then JAVA_HOME="$candidate"; break; fi
    done
    if [ -z "${JAVA_HOME:-}" ]; then
        echo "!! No $want found for Minecraft $MC. Set JAVA_HOME." >&2
        exit 1
    fi
    export JAVA_HOME
fi
export PATH="$JAVA_HOME/bin:$PATH"

LOG="$(mktemp)"
trap 'rm -f "$LOG"' EXIT

echo ">> Minecraft $MC, JDK at $JAVA_HOME"
echo ">> Booting the dev server with -Pselftest (first run decompiles Minecraft; be patient)"

# --stop the server itself once it has answered. Gradle cannot pipe stdin to a dev server, so the
# run is killed once both blocks have appeared rather than asked to shut down politely.
( ./gradlew runServer -Pselftest --console=plain > "$LOG" 2>&1 || true ) &
GRADLE_PID=$!

DEADLINE=$(( $(date +%s) + ${SELFTEST_TIMEOUT:-1800} ))
while true; do
    if grep -qE "self-test (PASSED|FAILED)" "$LOG" 2>/dev/null \
       && grep -qc "Factions self-test" "$LOG" >/dev/null 2>&1; then
        sleep 2   # let the second block finish printing
        break
    fi
    if grep -qE "^FAILURE: |Failed to initialize server" "$LOG" 2>/dev/null; then
        echo "!! The server failed to start." >&2
        tail -40 "$LOG" >&2
        kill $GRADLE_PID 2>/dev/null || true
        exit 1
    fi
    if [ "$(date +%s)" -gt "$DEADLINE" ]; then
        echo "!! Timed out waiting for the self-test." >&2
        tail -40 "$LOG" >&2
        kill $GRADLE_PID 2>/dev/null || true
        exit 1
    fi
    sleep 5
done

pkill -f "runServer" 2>/dev/null || true
kill $GRADLE_PID 2>/dev/null || true

echo
grep -E "self-test (PASSED|FAILED)" "$LOG" | sed 's/^.*\]: //' | sed 's/^/   /'
grep -E "  ✗ " "$LOG" | sed 's/^.*\]: //' | sed 's/^/   /' || true

if grep -q "self-test FAILED" "$LOG"; then
    echo
    echo "!! A self-test failed on Minecraft $MC." >&2
    exit 1
fi
if ! grep -q "Standards self-test PASSED" "$LOG"; then
    echo "!! Standards' self-test did not run at all." >&2
    exit 1
fi
echo
echo ">> Both green on Minecraft $MC."
