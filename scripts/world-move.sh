#!/usr/bin/env bash
# Moves a world between real Fabric and Paper servers and checks the stickers follow (AC-13,
# PRD §6.2 "World moved between platforms", owner decision 4):
#
#   1. Fabric: a decorated, full box is saved the Fabric way (our keys in the box's custom_data).
#   2. Paper, same world folder: the plugin moves the sticker into Paper's persistent data and
#      strips the stale custom_data copy. The sticker is then changed on Paper.
#   3. Fabric again, same world folder: the box shows Paper's change.
#
#   scripts/world-move.sh                   # extra args go to Gradle for the Paper run
#
# Logs: build/world-move/fabric-1.log, paper.log, fabric-2.log.
set -uo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
prop() { grep -E "^$1=" "$root/gradle.properties" | cut -d= -f2-; }
mc="$(prop minecraft_version)"
loader="$(prop fabric_loader_version)"
fabric_api="$(prop fabric_api_version)"
installer="1.1.2"
limit="${SMOKE_TIMEOUT:-300}"

dir="$root/build/world-move"
cache="$root/build/fabric-smoke/cache"
fabric="$dir/fabric"
paper="$dir/paper"
log=""
mkdir -p "$dir" "$cache"

fail() { echo "::error::world move: $*"; [ -n "$log" ] && [ -f "$log" ] && tail -n 60 "$log"; exit 1; }
fetch() { # url file
    [ -s "$2" ] || curl -fsSL -A "shulker-stickers-ci" -o "$2.part" "$1" && mv -f "$2.part" "$2" 2>/dev/null
    [ -s "$2" ] || fail "download failed: $1"
}
seen() { grep -qE "$1" "$log" 2>/dev/null; }
checks=0
expect() { # pattern message
    if grep -qE "$1" "$log"; then echo "PASS $2"; checks=$((checks + 1)); else fail "$2"; fi
}
expect_not() { # pattern message
    if grep -qE "$1" "$log"; then fail "$2"; else echo "PASS $2"; checks=$((checks + 1)); fi
}
no_errors() {
    if grep -nE '/ERROR\]|ERROR\]:|Exception' "$log" | grep -v 'Ambiguity'; then
        fail "errors in the server log (above)"
    fi
}

# Writes console commands once the server is up ("WAIT n" pauses), waits for END-OF-CHECKS,
# then stops the server. Every wait is bounded.
feed() {
    local i=0 cmd
    until seen 'Done \([0-9.]+s\)!'; do
        i=$((i + 1)); [ "$i" -gt "$limit" ] && { echo stop; return; }; sleep 1
    done
    for cmd in "$@"; do
        case "$cmd" in WAIT\ *) sleep "${cmd#WAIT }" ;; *) echo "$cmd" ;; esac
    done
    echo "say END-OF-CHECKS"
    i=0
    until seen 'END-OF-CHECKS'; do
        i=$((i + 1)); [ "$i" -gt 60 ] && break; sleep 1
    done
    sleep 1
    echo stop
}

finish() { # after a run: plain text, and it must have got through the commands
    sed -i -E 's/\x1b\[[0-9;]*m//g' "$log"
    seen 'Done \([0-9.]+s\)!' || fail "the server did not start"
    seen 'END-OF-CHECKS' || fail "the console commands did not finish"
}

run_fabric() { # log commands...
    log="$1"; shift
    : > "$log"
    ( cd "$fabric" && feed "$@" | timeout --kill-after=30 $((limit + 120)) \
        java -Xmx2G -jar "$cache/fabric-server-$mc-$loader.jar" nogui ) >> "$log" 2>&1
    finish
}

echo "Building the jars"
"$root/gradlew" -p "$root" :fabric:assemble :paper:jar --console=plain -q || fail "build failed"
jar="$(ls -t "$root"/fabric/build/libs/shulker-stickers-fabric-*.jar | grep -v -- '-sources\.jar$' | head -n 1)"
fetch "https://meta.fabricmc.net/v2/versions/loader/$mc/$loader/$installer/server/jar" "$cache/fabric-server-$mc-$loader.jar"
api_url="$(curl -fsSL -A "shulker-stickers-ci" \
    "https://api.modrinth.com/v2/project/fabric-api/version/$(printf '%s' "$fabric_api" | sed 's/+/%2B/')" \
    | grep -oE '"url":"https://cdn\.modrinth\.com/[^"]+\.jar"' | head -n 1 | cut -d'"' -f4)"
[ -n "$api_url" ] || fail "could not find Fabric API $fabric_api on Modrinth"
fetch "$api_url" "$cache/fabric-api-$fabric_api.jar"

rm -rf "$fabric" "$paper"
mkdir -p "$fabric/mods" "$paper"
cp "$cache/fabric-api-$fabric_api.jar" "$jar" "$fabric/mods/"
echo "eula=true" > "$fabric/eula.txt"
cat > "$fabric/server.properties" <<'EOF'
level-type=minecraft\:flat
generator-settings={"layers"\:[{"block"\:"minecraft\:bedrock","height"\:1},{"block"\:"minecraft\:stone","height"\:3}],"biome"\:"minecraft\:plains"}
online-mode=false
server-port=25598
spawn-protection=0
view-distance=4
simulation-distance=4
EOF

# 1. Fabric: a box with our sticker in custom_data, the way Fabric saves it.
fabric_box='minecraft:red_shulker_box{Items:[{Slot:0b,id:"minecraft:diamond",count:5}],components:{"minecraft:custom_data":{PublicBukkitValues:{"shulker_stickers:v":1,"shulker_stickers:icon_id":"minecraft:diamond","shulker_stickers:anchor":"lid","shulker_stickers:rot":0}}}}'
echo "1. Fabric: saving a decorated box"
run_fabric "$dir/fabric-1.log" \
    "forceload add 0 0" \
    "setblock 1 -50 1 $fabric_box" \
    "data get block 1 -50 1 components"
expect 'custom_data.*shulker_stickers:icon_id.*minecraft:diamond' "Fabric saved the box with its sticker in custom_data"
no_errors

# 2. Paper on the same world.
echo "2. Paper: the same world"
cp -r "$fabric/world" "$paper/world"
log="$dir/paper.log"
: > "$log"
feed \
    "WAIT 3" \
    "data get block 1 -50 1 PublicBukkitValues" \
    "data get block 1 -50 1 components" \
    "data get block 1 -50 1 Items" \
    'data merge block 1 -50 1 {PublicBukkitValues:{"shulker_stickers:icon_id":"minecraft:emerald"}}' \
    "data get block 1 -50 1 PublicBukkitValues" \
    | timeout --kill-after=30 $((limit + 120)) "$root/gradlew" -p "$root" :paper:runWorldMove --console=plain "$@" >> "$log" 2>&1
finish
expect '\[ShulkerStickers\]' "the plugin is loaded"
# The first PublicBukkitValues answer is before the edit.
first_pdc="$(grep -m1 -E 'has the following block data: \{.*shulker_stickers' "$log")"
grep -q 'shulker_stickers:icon_id": "minecraft:diamond"' <<< "$first_pdc" \
    || fail "Paper didn't move the Fabric sticker into its persistent data (first answer: ${first_pdc:-none})"
echo "PASS Paper moved the Fabric sticker into its persistent data"; checks=$((checks + 1))
expect_not 'has the following block data: \{"minecraft:custom_data": \{PublicBukkitValues: \{[^}]*shulker_stickers' \
    "Paper removed the stale copy from custom_data"
expect 'has the following block data: \[\{[^]]*count: 5' "the box's contents are intact on Paper"
expect 'shulker_stickers:icon_id": "minecraft:emerald"' "the sticker was changed on Paper"
no_errors

# 3. Back to Fabric.
echo "3. Fabric: the world back from Paper"
rm -rf "$fabric/world"
cp -r "$paper/world" "$fabric/world"
# Paper 26.x moves world_gen_settings.dat into each dimension's folder when it first opens a
# vanilla world; vanilla and Fabric won't start without it in data/minecraft/. Owners moving a
# world from Paper to Fabric need the same step (README, "Moving a world between platforms").
gen="$fabric/world/dimensions/minecraft/overworld/data/minecraft/world_gen_settings.dat"
if [ -f "$gen" ] && [ ! -f "$fabric/world/data/minecraft/world_gen_settings.dat" ]; then
    cp "$gen" "$fabric/world/data/minecraft/world_gen_settings.dat"
fi
run_fabric "$dir/fabric-2.log" \
    "data get block 1 -50 1 components" \
    "data get block 1 -50 1 Items"
expect 'custom_data.*shulker_stickers:icon_id.*minecraft:emerald' "Fabric shows the sticker as changed on Paper"
expect_not 'custom_data.*shulker_stickers:icon_id.*minecraft:diamond' "no trace of the old sticker on Fabric"
expect 'has the following block data: \[\{[^]]*count: 5' "the box's contents are intact back on Fabric"
no_errors

echo "World move check: all $checks checks passed"
