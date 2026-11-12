#!/usr/bin/env bash
# The uninstall check on Paper (AC-6). Runs the self-test with the plugin (which leaves a
# decorated, locked, full box at 2 250 2), then restarts the same world WITHOUT the plugin and
# checks from the console that the world loads, the box keeps its contents and data, no display
# entities are left, and breaking the box drops it with its contents (vanilla loot).
#
#   scripts/paper-uninstall.sh                     # extra args go to Gradle, e.g. -Pserverport=25581
#
# Logs: build/selftest/runServer.log (with the plugin), build/selftest/uninstalled.log (without).
set -uo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
limit="${SELFTEST_TIMEOUT:-900}"
log="$root/build/selftest/uninstalled.log"

fail() { echo "::error::paper uninstall check: $*"; [ -f "$log" ] && tail -n 60 "$log"; exit 1; }

"$root/scripts/selftest.sh" runServer "$@" || fail "the self-test with the plugin failed"

seen() { grep -qE "$1" "$log" 2>/dev/null; }

# Console commands, sent once the server is up. Entities load a little after their chunks.
feed() {
    local i=0
    until seen 'Done \([0-9.]+s\)!'; do
        i=$((i + 1)); [ "$i" -gt "$limit" ] && { echo stop; return; }; sleep 1
    done
    echo "forceload add 0 0 15 15"
    sleep 8
    echo "execute if block 2 250 2 minecraft:shulker_box"
    echo "data get block 2 250 2 Items[26]"
    echo "data get block 2 250 2 PublicBukkitValues"
    echo "execute if entity @e[type=minecraft:item_display]"
    echo "execute if entity @e[type=minecraft:text_display]"
    echo "kill @e[type=minecraft:item]"
    echo "setblock 2 250 2 minecraft:air destroy"
    sleep 3
    echo "data get entity @e[type=minecraft:item,limit=1] Item"
    echo "say END-OF-CHECKS"
    i=0
    until seen 'END-OF-CHECKS'; do
        i=$((i + 1)); [ "$i" -gt 60 ] && break; sleep 1
    done
    sleep 1
    echo stop
}

echo "Restarting the world without the plugin"
: > "$log"
feed | timeout --kill-after=30 "$limit" "$root/gradlew" -p "$root" :paper:runWithoutPlugin --console=plain "$@" >> "$log" 2>&1
status=$?
[ "$status" -eq 124 ] || [ "$status" -eq 137 ] && fail "the server did not stop within ${limit}s"
seen 'Done \([0-9.]+s\)!' || fail "the world did not load without the plugin"
seen 'END-OF-CHECKS' || fail "the console checks did not finish"
# Paper colors console output; match on plain text.
sed -i -E 's/\x1b\[[0-9;]*m//g' "$log"

checks=0
expect() { # pattern message
    if grep -qE "$1" "$log"; then echo "PASS $2"; checks=$((checks + 1)); else fail "$2"; fi
}
seen '\[ShulkerStickers\]' && fail "the plugin was still loaded"
expect 'Test passed' "the box is still there"
expect 'has the following block data: \{[^}]*Slot: 26b' "all 27 stacks are still inside"
expect 'has the following block data: \{[^}]*shulker_stickers:' "the sticker data is kept for a reinstall"
[ "$(grep -c 'Test failed' "$log")" -eq 2 ] || fail "display entities were left in the world"
echo "PASS no display entities left in the world"; checks=$((checks + 1))
expect 'has the following entity data: \{.*minecraft:shulker_box.*minecraft:container' \
    "breaking the box drops it with its contents"
if grep -nE '/ERROR\]|Exception' "$log"; then
    fail "errors in the server log (above)"
fi
echo "PASS no errors in the server log"; checks=$((checks + 1))
echo "Paper uninstall check: all $checks checks passed"
