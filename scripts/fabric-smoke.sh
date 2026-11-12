#!/usr/bin/env bash
# Starts a real Fabric dedicated server with only Fabric API and the release jar in mods/, then
# checks the mod works from the jar players will install (GameTests run from the dev classpath,
# so they can't catch a jar that's missing a bundled library or a mixin config).
#
# It runs `/shulkerstickers help` and `reload` from the console, and places a shulker box whose
# NBT is what a Paper server saves: vanilla drops the unknown root key, so our sticker only shows
# up in the box's components if our load mixin ran (owner decision 4).
#
#   scripts/fabric-smoke.sh            # builds the jar first
#   SMOKE_TIMEOUT=600 scripts/fabric-smoke.sh
#
# The server log is kept in build/fabric-smoke/server.log.
set -uo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
prop() { grep -E "^$1=" "$root/gradle.properties" | cut -d= -f2-; }

mc="$(prop minecraft_version)"
loader="$(prop fabric_loader_version)"
fabric_api="$(prop fabric_api_version)"
installer="1.1.2"
limit="${SMOKE_TIMEOUT:-600}"

dir="$root/build/fabric-smoke"
cache="$dir/cache"
server="$dir/server"
log="$dir/server.log"
mkdir -p "$cache"

fail() { echo "::error::fabric smoke test: $*"; [ -f "$log" ] && tail -n 60 "$log"; exit 1; }

fetch() { # url file
    [ -s "$2" ] || curl -fsSL -A "shulker-stickers-ci" -o "$2.part" "$1" && mv -f "$2.part" "$2" 2>/dev/null
    [ -s "$2" ] || fail "download failed: $1"
}

echo "Building the Fabric jar"
"$root/gradlew" -p "$root" :fabric:assemble --console=plain -q || fail "build failed"
# The newest jar: older versions may still sit in build/libs on a dev machine.
jar="$(ls -t "$root"/fabric/build/libs/shulker-stickers-fabric-*.jar | grep -v -- '-sources\.jar$' | head -n 1)"
[ -n "$jar" ] || fail "no Fabric jar in fabric/build/libs"

fetch "https://meta.fabricmc.net/v2/versions/loader/$mc/$loader/$installer/server/jar" "$cache/fabric-server-$mc-$loader.jar"
api_url="$(curl -fsSL -A "shulker-stickers-ci" \
    "https://api.modrinth.com/v2/project/fabric-api/version/$(printf '%s' "$fabric_api" | sed 's/+/%2B/')" \
    | grep -oE '"url":"https://cdn\.modrinth\.com/[^"]+\.jar"' | head -n 1 | cut -d'"' -f4)"
[ -n "$api_url" ] || fail "could not find Fabric API $fabric_api on Modrinth"
fetch "$api_url" "$cache/fabric-api-$fabric_api.jar"

# A fresh server each run: flat world, offline, its own port so it can run next to a dev server.
rm -rf "$server"
mkdir -p "$server/mods"
cp "$cache/fabric-api-$fabric_api.jar" "$jar" "$server/mods/"
echo "eula=true" > "$server/eula.txt"
cat > "$server/server.properties" <<'EOF'
level-type=minecraft\:flat
generator-settings={"layers"\:[{"block"\:"minecraft\:bedrock","height"\:1},{"block"\:"minecraft\:stone","height"\:3}],"biome"\:"minecraft\:plains"}
online-mode=false
server-port=25599
spawn-protection=0
view-distance=4
simulation-distance=4
EOF

# Paper's layout for a box with our sticker: the keys sit in the root PublicBukkitValues.
# It also holds 5 diamonds, to check contents survive the uninstall phase.
box='minecraft:red_shulker_box{Items:[{Slot:0b,id:"minecraft:diamond",count:5}],PublicBukkitValues:{"shulker_stickers:v":1,"shulker_stickers:icon_id":"minecraft:diamond","shulker_stickers:anchor":"lid","shulker_stickers:rot":0}}'

seen() { grep -qE "$1" "$log" 2>/dev/null; }

# Writes the given console commands to the server's stdin once it is ready, waits for the
# answer pattern, then sends `stop` and exits, which closes stdin. Every wait is bounded, so a
# broken server still gets `stop`.
feed() { # answer-pattern command...
    local answer="$1" i=0
    shift
    until seen 'Done \([0-9.]+s\)!'; do
        i=$((i + 1)); [ "$i" -gt "$limit" ] && { echo stop; return; }; sleep 1
    done
    printf '%s\n' "$@"
    i=0
    until seen "$answer"; do
        i=$((i + 1)); [ "$i" -gt 60 ] && break; sleep 1
    done
    sleep 2
    echo stop
}

# Runs one server session with the given commands; the log goes to $log.
run_server() { # answer-pattern command...
    : > "$log"
    ( cd "$server" && feed "$@" | java -Xmx2G -jar "$cache/fabric-server-$mc-$loader.jar" nogui ) >> "$log" 2>&1 &
    local run_pid=$! waited=0
    # Bounded: the startup limit plus two minutes for the commands and shutdown.
    while kill -0 "$run_pid" 2>/dev/null; do
        if [ "$waited" -gt $((limit + 120)) ]; then
            pkill -f "fabric-server-$mc-$loader.jar" 2>/dev/null || true
            fail "the server did not stop"
        fi
        sleep 1; waited=$((waited + 1))
    done
    seen 'Done \([0-9.]+s\)!' || fail "the server did not finish starting"
    seen "$1" || fail "no answer to the last command"
}

checks=0
expect() { # pattern message
    if grep -qE "$1" "$log"; then echo "PASS $2"; checks=$((checks + 1)); else fail "$2"; fi
}
no_errors() {
    if grep -nE '/ERROR\]|Exception|Mixin apply.*failed|Critical injection failure' "$log" | grep -v 'Ambiguity' ; then
        fail "errors in the server log (above)"
    fi
    echo "PASS no errors in the server log"; checks=$((checks + 1))
}

# Phase 1: with the mod.
echo "Starting Fabric $loader on $mc with $(basename "$jar") and Fabric API $fabric_api"
run_server 'has the following block data' \
    "shulkerstickers help" \
    "shulkerstickers reload" \
    "forceload add 0 0" \
    "setblock 1 -50 1 $box" \
    "data get block 1 -50 1 components"
expect 'Shulker Stickers loaded' "the mod starts from the release jar"
expect 'polymer-virtual-entity' "bundled Polymer loads"
expect 'Shulker Stickers: sneak and right-click' "/shulkerstickers help answers in English"
expect 'Shulker Stickers config reloaded' "/shulkerstickers reload works"
expect 'custom_data.*shulker_stickers:icon_id.*minecraft:diamond' "a Paper-saved box keeps its sticker (load mixin ran)"
[ -f "$server/config/shulker_stickers.json" ] || fail "no default config written"
echo "PASS default config written"; checks=$((checks + 1))
no_errors
cp "$log" "$dir/server-with-mod.log"

# Phase 2: restart with the mod. The decorated box now loads from disk with its chunk; drawing
# it must not ask for the chunk that is still loading (that deadlocked the server thread).
echo "Restarting with the mod (decorated box loads from disk)"
run_server 'END-OF-CHECKS' \
    "data get block 1 -50 1 components" \
    "say END-OF-CHECKS"
expect 'Shulker Stickers loaded' "the mod starts again"
expect 'custom_data.*shulker_stickers:icon_id.*minecraft:diamond' "a saved decorated box loads without hanging the server"
no_errors
cp "$log" "$dir/server-restarted.log"

# Phase 3 (AC-6): the same world with the mod removed.
echo "Restarting without Shulker Stickers (uninstall check)"
rm -f "$server/mods/$(basename "$jar")"
run_server 'END-OF-CHECKS' \
    "execute if block 1 -50 1 minecraft:red_shulker_box" \
    "data get block 1 -50 1 Items" \
    "data get block 1 -50 1 components" \
    "execute if entity @e[type=minecraft:item_display]" \
    "execute if entity @e[type=minecraft:text_display]" \
    "say END-OF-CHECKS"
grep -q 'Shulker Stickers loaded' "$log" && fail "the mod was still loaded"
expect 'Test passed' "the box is still a red shulker box"
expect 'has the following block data: \[\{[^]]*(count: 5[^]]*"minecraft:diamond"|"minecraft:diamond"[^]]*count: 5)' "its 5 diamonds are still inside"
expect 'custom_data.*shulker_stickers:icon_id' "its sticker data is kept for a reinstall"
[ "$(grep -c 'Test failed' "$log")" -eq 2 ] || fail "display entities were left in the world"
echo "PASS no display entities left in the world"; checks=$((checks + 1))
no_errors
cp "$log" "$dir/server-uninstalled.log"

# Phase 4 (part of AC-14): reinstalled next to Ultimate Shulker Trims, the only competing
# shulker mod with a 26.3 build (Paint your Shulkers! and HerrShulker stop at 1.21.x).
trims_url="https://cdn.modrinth.com/data/253aEGkT/versions/6wmF0WPI/ultimate_shulker_trims-26.3.jar"
fetch "$trims_url" "$cache/ultimate_shulker_trims-26.3.jar"
cp "$jar" "$cache/ultimate_shulker_trims-26.3.jar" "$server/mods/"
echo "Restarting with Shulker Stickers and Ultimate Shulker Trims"
run_server 'END-OF-CHECKS' \
    "shulkerstickers reload" \
    "data get block 1 -50 1 components" \
    "say END-OF-CHECKS"
expect 'Shulker Stickers loaded' "Shulker Stickers loads next to Ultimate Shulker Trims"
expect 'ultimate_shulker_trims' "Ultimate Shulker Trims loads"
expect 'Shulker Stickers config reloaded' "/shulkerstickers reload still works"
expect 'custom_data.*shulker_stickers:icon_id.*minecraft:diamond' "the box's sticker is still there after the reinstall"
no_errors
cp "$log" "$dir/server-with-trims.log"

echo "Fabric smoke test: all $checks checks passed"
