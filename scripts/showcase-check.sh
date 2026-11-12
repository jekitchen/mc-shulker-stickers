#!/usr/bin/env bash
# Checks the showcase datapack (showcase/shulker_stickers_showcase) loads and builds on a real
# Fabric server with the release jar: the function parses (a syntax error drops the whole
# function), runs without errors, and leaves boxes with their stickers. Uses the downloads cached
# by scripts/fabric-smoke.sh (run that first, or let this script fetch them).
#
#   scripts/showcase-check.sh
set -uo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
prop() { grep -E "^$1=" "$root/gradle.properties" | cut -d= -f2-; }
mc="$(prop minecraft_version)"
loader="$(prop fabric_loader_version)"
fabric_api="$(prop fabric_api_version)"
limit="${SMOKE_TIMEOUT:-300}"
cache="$root/build/fabric-smoke/cache"
dir="$root/build/showcase-check"
server="$dir/server"
log="$dir/server.log"

fail() { echo "::error::showcase check: $*"; [ -f "$log" ] && tail -n 40 "$log"; exit 1; }
seen() { grep -qE "$1" "$log" 2>/dev/null; }

server_jar="$cache/fabric-server-$mc-$loader.jar"
api_jar="$cache/fabric-api-$fabric_api.jar"
if [ ! -s "$server_jar" ] || [ ! -s "$api_jar" ]; then
    echo "Fetching the Fabric server via scripts/fabric-smoke.sh"
    "$root/scripts/fabric-smoke.sh" > /dev/null || fail "scripts/fabric-smoke.sh failed"
fi
"$root/gradlew" -p "$root" :fabric:assemble --console=plain -q || fail "build failed"
jar="$(ls -t "$root"/fabric/build/libs/shulker-stickers-fabric-*.jar | grep -v -- '-sources\.jar$' | head -n 1)"

rm -rf "$server"
mkdir -p "$server/mods" "$server/world/datapacks"
cp "$api_jar" "$jar" "$server/mods/"
cp -r "$root/showcase/shulker_stickers_showcase" "$server/world/datapacks/"
echo "eula=true" > "$server/eula.txt"
cat > "$server/server.properties" <<'EOF'
level-type=minecraft\:flat
generator-settings={"layers"\:[{"block"\:"minecraft\:bedrock","height"\:1},{"block"\:"minecraft\:stone","height"\:3}],"biome"\:"minecraft\:plains"}
online-mode=false
server-port=25596
spawn-protection=0
view-distance=4
simulation-distance=4
EOF

feed() {
    local i=0
    until seen 'Done \([0-9.]+s\)!'; do
        i=$((i + 1)); [ "$i" -gt "$limit" ] && { echo stop; return; }; sleep 1
    done
    echo "forceload add 0 0 31 31"
    sleep 2
    echo "execute positioned 0 -60 0 run function shulker_stickers_showcase:build"
    sleep 2
    echo "data get block 0 -58 3 components"
    echo "data get block 0 -58 6 components"
    echo "data get block 0 -58 9 components"
    echo "data get block 0 -58 12 lock"
    echo "data get block 2 -59 12 Items"
    echo "say END-OF-CHECKS"
    i=0
    until seen 'END-OF-CHECKS'; do
        i=$((i + 1)); [ "$i" -gt 60 ] && break; sleep 1
    done
    sleep 1
    echo stop
}

: > "$log"
( cd "$server" && feed | timeout --kill-after=30 $((limit + 120)) java -Xmx2G -jar "$server_jar" nogui ) >> "$log" 2>&1
sed -i -E 's/\r$//' "$log"
seen 'END-OF-CHECKS' || fail "the server didn't get through the commands"

checks=0
expect() { if grep -qE "$1" "$log"; then echo "PASS $2"; checks=$((checks + 1)); else fail "$2"; fi; }
if grep -nE 'Failed to load function|Unknown or incomplete command|Couldn.t load|/ERROR\]|Exception' "$log"; then
    fail "errors while loading or running the function (above)"
fi
echo "PASS the function loads and runs without errors"; checks=$((checks + 1))
expect 'Executed [0-9]+ command|Running function shulker_stickers_showcase:build' "the function ran"
# The answer for a position must contain every given text (key order varies).
expect_answer() { # position message text...
    local line pos="$1" msg="$2"
    shift 2
    line="$(grep -m1 "$pos has the following block data" "$log")"
    [ -n "$line" ] || fail "$msg (no answer for $pos)"
    for text in "$@"; do
        grep -qF -- "$text" <<< "$line" || fail "$msg (missing $text in: $line)"
    done
    echo "PASS $msg"; checks=$((checks + 1))
}
expect_answer "0, -58, 3" "row 1: a lid icon and a name" '"minecraft:custom_name": "Up"' '"shulker_stickers:icon_id": "minecraft:diamond"' '"shulker_stickers:anchor": "lid"'
expect_answer "0, -58, 6" "row 2: a side icon" '"shulker_stickers:anchor": "side"' '"shulker_stickers:face": "north"'
expect_answer "0, -58, 9" "row 3: a dyed icon" 'icon_visual' 'dyed_color'
expect_answer "0, -58, 12" "row 4: the box is locked with a stick" 'minecraft:stick'
expect_answer "2, -59, 12" "row 4: the key is in the chest" 'minecraft:stick'
echo "Showcase check: all $checks checks passed"
