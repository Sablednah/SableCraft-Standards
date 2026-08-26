#!/usr/bin/env bash
# Build Standards AND Factions ReForged, and copy both jars into a CurseForge NeoForge test
# instance's mods/ folder, then launch that instance from CurseForge to test.
#
# Both, because Factions hard-depends on Standards: deploying one without the other gives a
# NeoForge "missing dependency" screen rather than a test, and deploying a NEW Standards beside
# an OLD Factions is worse still — it starts, and then misbehaves somewhere unrelated.
#
# Usage:  ./deploy.sh
#         STANDARDS_INSTANCE="/path/to/some other instance" ./deploy.sh
#         WITH_FACTIONS=0 ./deploy.sh          # Standards alone
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"

# No bundled JDK in this repo (yet); borrow MobHealth's portable JDK 21. Honour a JAVA_HOME
# the caller has already set — on newer Minecraft lines this needs to be a JDK 25.
if [ -z "${JAVA_HOME:-}" ]; then
    if [ -d "$ROOT/tools/jdk21" ]; then
        export JAVA_HOME="$ROOT/tools/jdk21"
    elif [ -d "$ROOT/../MobHealth-Forge/tools/jdk21" ]; then
        export JAVA_HOME="$ROOT/../MobHealth-Forge/tools/jdk21"
    else
        echo "!! No portable JDK found (tools/jdk21 here or in ../MobHealth-Forge)." >&2
        exit 1
    fi
fi
export PATH="$JAVA_HOME/bin:$PATH"

INSTANCE="${STANDARDS_INSTANCE:-/mnt/c/Users/darre/curseforge/minecraft/Instances/Standards}"
MODS="$INSTANCE/mods"

WITH_FACTIONS="${WITH_FACTIONS:-1}"

echo ">> Building Standards..."
"$ROOT/gradlew" build --console=plain
if [ "$WITH_FACTIONS" = "1" ]; then
    echo ">> Building Factions ReForged..."
    "$ROOT/gradlew" :factions:build --console=plain
fi

if [ ! -d "$MODS" ]; then
    echo "!! Instance mods folder not found: $MODS" >&2
    exit 1
fi

JAR="$(ls -t "$ROOT"/build/libs/standards-*.jar 2>/dev/null | grep -v -- '-sources' | head -1 || true)"
if [ -z "$JAR" ]; then
    echo "!! No built jar found in build/libs" >&2
    exit 1
fi

# A running instance holds the jar open, so Windows refuses to replace it. Say so plainly: this
# otherwise fails looking like a success, and you test a stale jar wondering why nothing changed.
instance_locked() {
    echo "!! Could not $1 the jar in the instance's mods folder." >&2
    echo "!! Is the '$(basename "$INSTANCE")' instance still running? Close Minecraft and retry." >&2
    exit 1
}

echo ">> Removing previous Standards jars from the instance..."
rm -f "$MODS"/standards-*.jar || instance_locked "remove"

cp "$JAR" "$MODS/" || instance_locked "copy"

# Confirm the jar really landed and matches: a half-written copy is worse than a loud failure.
if ! cmp -s "$JAR" "$MODS/$(basename "$JAR")"; then
    echo "!! The deployed jar does not match the one just built." >&2
    exit 1
fi

echo ">> Deployed: $(basename "$JAR") ($(stat -c%s "$JAR") bytes)"

if [ "$WITH_FACTIONS" = "1" ]; then
    FJAR="$(ls -t "$ROOT"/../Factions-ReForged/build/libs/factions-*.jar 2>/dev/null \
        | grep -v -- '-sources' | head -1 || true)"
    if [ -z "$FJAR" ]; then
        echo "!! No built Factions jar found. Set WITH_FACTIONS=0 to deploy Standards alone." >&2
        exit 1
    fi
    echo ">> Removing previous Factions jars from the instance..."
    rm -f "$MODS"/factions-*.jar || instance_locked "remove"
    cp "$FJAR" "$MODS/" || instance_locked "copy"
    if ! cmp -s "$FJAR" "$MODS/$(basename "$FJAR")"; then
        echo "!! The deployed Factions jar does not match the one just built." >&2
        exit 1
    fi
    echo ">> Deployed: $(basename "$FJAR") ($(stat -c%s "$FJAR") bytes)"
fi

echo ">> Launch the '$(basename "$INSTANCE")' instance in CurseForge to test."
