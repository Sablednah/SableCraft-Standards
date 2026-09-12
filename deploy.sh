#!/usr/bin/env bash
# Build Standards AND Factions ReForged and copy both into every CurseForge test instance that
# already carries them, matching each instance to the jar built for its Minecraft version.
#
# Both mods, because Factions hard-depends on Standards: deploying one without the other gives a
# NeoForge "missing dependency" screen rather than a test, and a NEW Standards beside an OLD
# Factions starts and then misbehaves somewhere unrelated.
#
# Usage:  ./deploy.sh                       every instance that already has our jars
#         ./deploy.sh 26.2 26.1.2           only those, by folder name
#         STANDARDS_INSTANCE="/path/x" ./deploy.sh    one explicit path (the old behaviour)
#         WITH_FACTIONS=0 ./deploy.sh       Standards alone
#         SKIP_BUILD=1 ./deploy.sh          deploy what is already in build/libs
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
INSTANCES_DIR="/mnt/c/Users/darre/curseforge/minecraft/Instances"
WITH_FACTIONS="${WITH_FACTIONS:-1}"

# No system Java. Borrow the portable JDK from the first mod in the series. Honour a JAVA_HOME the
# caller has already set — on newer Minecraft lines this needs to be a JDK 25.
if [ -z "${JAVA_HOME:-}" ]; then
    if   [ -d "$ROOT/tools/jdk21" ];                  then export JAVA_HOME="$ROOT/tools/jdk21"
    elif [ -d "$ROOT/../MobHealth-Forge/tools/jdk21" ]; then export JAVA_HOME="$ROOT/../MobHealth-Forge/tools/jdk21"
    else echo "!! No portable JDK found (tools/jdk21 here or in ../MobHealth-Forge)." >&2; exit 1
    fi
fi
export PATH="$JAVA_HOME/bin:$PATH"

# --- which instances -------------------------------------------------------------------------

# An instance names its own Minecraft version in minecraftinstance.json, so the mapping is read
# rather than hardcoded — there are four of these now and a hand-kept list would rot.
mc_version_of() {
    python3 - "$1/minecraftinstance.json" <<'PY' 2>/dev/null || true
import json, sys
try:
    d = json.load(open(sys.argv[1]))
    print(d.get('gameVersion') or d.get('baseModLoader', {}).get('minecraftVersion', ''))
except Exception:
    pass
PY
}

targets=()
if [ -n "${STANDARDS_INSTANCE:-}" ]; then
    targets+=("$STANDARDS_INSTANCE")
elif [ "$#" -gt 0 ]; then
    for name in "$@"; do targets+=("$INSTANCES_DIR/$name"); done
else
    # Every instance we have deployed to before — identified by already carrying a standards jar,
    # so a brand new instance is opted in by copying one jar into it once rather than by editing
    # this script.
    while IFS= read -r m; do targets+=("$(dirname "$m")"); done \
        < <(ls -d "$INSTANCES_DIR"/*/mods 2>/dev/null | while read -r d; do
                ls "$d"/standards-*.jar >/dev/null 2>&1 && echo "$d"
            done)
fi
[ "${#targets[@]}" -gt 0 ] || { echo "!! No instances found to deploy to." >&2; exit 1; }

# --- build -----------------------------------------------------------------------------------

if [ "${SKIP_BUILD:-0}" != "1" ]; then
    echo ">> Building Standards ($(git -C "$ROOT" rev-parse --abbrev-ref HEAD))..."
    "$ROOT/gradlew" build --console=plain
    if [ "$WITH_FACTIONS" = "1" ]; then
        echo ">> Building Factions ReForged..."
        "$ROOT/gradlew" :factions:build --console=plain
    fi
fi

# ⚠ The probe is a RENAME, never a write. Windows lets a running game's jar be WRITTEN while
# refusing to delete it, so cp and cmp both "succeed" and the bytes change underneath a JVM that
# has the old zip mapped. The game then dies later with
#   ZipException: invalid LOC header (bad signature)
# on the first class it loads lazily. That corrupted a Factions jar and crashed a world on
# 2026-09-12, because a hand-rolled copy loop skipped this check.
locked() {
    local victim="$1"
    [ -f "$victim" ] || return 1
    mv "$victim" "$victim.probe" 2>/dev/null && mv "$victim.probe" "$victim" 2>/dev/null && return 1
    return 0
}

# Find the jar built for one Minecraft version. Filenames carry it: standards-1.8.0+mc26.2.jar.
jar_for() {
    ls -t "$1"/*"+mc$2.jar" 2>/dev/null | grep -v -- '-sources' | head -1 || true
}

stamp_of() {
    unzip -p "$1" "$2/build.properties" 2>/dev/null | sed -n 's/^commit=\(.*\)$/\1/p' | head -1
}

install_one() {
    local jar="$1" mods="$2" name="$3"
    local base; base="$(basename "$jar")"
    if locked "$(ls "$mods/$name"-*.jar 2>/dev/null | head -1)"; then
        echo "   !! $name: instance is RUNNING — close Minecraft and retry. Nothing copied."
        return 1
    fi
    rm -f "$mods/$name"-*.jar
    cp "$jar" "$mods/"
    # A half-written copy is worse than a loud failure, and a jar that is not a valid zip is worse
    # still: it loads and then throws on the first lazily-loaded class, minutes later.
    if ! cmp -s "$jar" "$mods/$base" || ! unzip -t "$mods/$base" >/dev/null 2>&1; then
        echo "   !! $name: copy did not verify — jar may be corrupt." >&2
        return 1
    fi
    echo "   $base  ($(stamp_of "$jar" "$name"))"
}

# --- deploy ----------------------------------------------------------------------------------

fails=0
for inst in "${targets[@]}"; do
    mods="$inst/mods"
    label="$(basename "$inst")"
    [ -d "$mods" ] || { echo ">> $label: no mods folder, skipped"; continue; }

    mc="$(mc_version_of "$inst")"
    [ -n "$mc" ] || { echo ">> $label: cannot read its Minecraft version, skipped"; continue; }

    sjar="$(jar_for "$ROOT/build/libs" "$mc")"
    if [ -z "$sjar" ]; then
        echo ">> $label (mc $mc): no standards jar built for this line — build its branch first, skipped"
        continue
    fi

    echo ">> $label (mc $mc)"
    install_one "$sjar" "$mods" standards || fails=$((fails + 1))

    if [ "$WITH_FACTIONS" = "1" ]; then
        fjar="$(jar_for "$ROOT/../Factions-ReForged/build/libs" "$mc")"
        if [ -z "$fjar" ]; then
            echo "   .. no factions jar for mc $mc, skipped"
        else
            install_one "$fjar" "$mods" factions || fails=$((fails + 1))
        fi
    fi
done

[ "$fails" -eq 0 ] || { echo; echo "!! $fails deployment(s) failed — see above." >&2; exit 1; }
echo
echo ">> Done. Launch an instance in CurseForge to test."
