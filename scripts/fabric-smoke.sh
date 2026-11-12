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
jar="$(ls "$root"/fabric/build/libs/shulker-stickers-fabric-*.jar | grep -v -- '-sources\.jar$' | head -n 1)"
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
box='minecraft:shulker_box{PublicBukkitValues:{"shulker_stickers:v":1,"shulker_stickers:icon_id":"minecraft:diamond","shulker_stickers:anchor":"lid","shulker_stickers:rot":0}}'

seen() { grep -qE "$1" "$log" 2>/dev/null; }

# Writes console commands to the server's stdin once it is ready, then sends `stop` and exits,
# which closes stdin. Every wait is bounded, so a broken server still gets `stop`.
feed() {
    local i=0
    until seen 'Done \([0-9.]+s\)!'; do
        i=$((i + 1)); [ "$i" -gt "$limit" ] && { echo stop; return; }; sleep 1
    done
    echo "shulkerstickers help"
    echo "shulkerstickers reload"
    echo "forceload add 0 0"
    echo "setblock 1 -50 1 $box"
    echo "data get block 1 -50 1 components"
    i=0
    until seen 'has the following block data'; do
        i=$((i + 1)); [ "$i" -gt 60 ] && break; sleep 1
    done
    echo stop
}

echo "Starting Fabric $loader on $mc with $(basename "$jar") and Fabric API $fabric_api"
: > "$log"
( cd "$server" && feed | java -Xmx2G -jar "$cache/fabric-server-$mc-$loader.jar" nogui ) >> "$log" 2>&1 &
run_pid=$!

# The whole run is bounded too: the startup limit plus two minutes for the commands and shutdown.
waited=0
while kill -0 "$run_pid" 2>/dev/null; do
    if [ "$waited" -gt $((limit + 120)) ]; then
        pkill -f "fabric-server-$mc-$loader.jar" 2>/dev/null || true
        fail "the server did not stop"
    fi
    sleep 1; waited=$((waited + 1))
done
seen 'Done \([0-9.]+s\)!' || fail "the server did not finish starting"
seen 'has the following block data' || fail "no answer to /data get"

checks=0
expect() { # pattern message
    if grep -qE "$1" "$log"; then echo "PASS $2"; checks=$((checks + 1)); else fail "$2"; fi
}
expect 'Shulker Stickers loaded' "the mod starts from the release jar"
expect 'polymer-virtual-entity' "bundled Polymer loads"
expect 'Shulker Stickers: sneak and right-click' "/shulkerstickers help answers in English"
expect 'Shulker Stickers config reloaded' "/shulkerstickers reload works"
expect 'custom_data.*shulker_stickers:icon_id.*minecraft:diamond' "a Paper-saved box keeps its sticker (load mixin ran)"
[ -f "$server/config/shulker_stickers.json" ] || fail "no default config written"
echo "PASS default config written"; checks=$((checks + 1))

if grep -nE '/ERROR\]|Exception|Mixin apply.*failed|Critical injection failure' "$log" | grep -v 'Ambiguity' ; then
    fail "errors in the server log (above)"
fi
echo "PASS no errors in the server log"; checks=$((checks + 1))
echo "Fabric smoke test: all $checks checks passed"
