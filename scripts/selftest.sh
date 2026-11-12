#!/usr/bin/env bash
# Runs the Paper plugin's real-server self-test on one server and fails unless every check passed.
#
#   scripts/selftest.sh runServer      # Paper
#   scripts/selftest.sh runFolia       # Folia
#   scripts/selftest.sh runCanvas      # Canvas
#   scripts/selftest.sh runClaims      # Paper with WorldEdit, WorldGuard and GriefPrevention
#
# Extra arguments go to Gradle (for example -Pnodatapack, or -Pserverport=25575 to run next to
# another server). SELFTEST_TIMEOUT sets the limit in seconds (default 900). The server log is
# kept in build/selftest/<task>.log.
set -uo pipefail

task="${1:?usage: scripts/selftest.sh <runServer|runFolia|runCanvas|runClaims> [gradle args]}"
shift
limit="${SELFTEST_TIMEOUT:-900}"

root="$(cd "$(dirname "$0")/.." && pwd)"
log_dir="$root/build/selftest"
log="$log_dir/$task.log"
mkdir -p "$log_dir"

echo "Running :paper:$task with the self-test (limit ${limit}s); log: $log"
# stdin from /dev/null: the server console must not wait for input.
timeout --kill-after=30 "$limit" "$root/gradlew" -p "$root" ":paper:$task" -Pselftest --console=plain "$@" \
    < /dev/null > "$log" 2>&1
status=$?

grep -E "SELFTEST (FAIL|DONE)" "$log" || true

if [ "$status" -eq 124 ] || [ "$status" -eq 137 ]; then
    echo "::error::$task: the server did not stop within ${limit}s"
    exit 1
fi

done_line="$(grep -E "SELFTEST DONE pass=[0-9]+ fail=[0-9]+" "$log" | tail -n 1)"
if [ -z "$done_line" ]; then
    echo "::error::$task: no SELFTEST DONE line (Gradle exit $status); see $log"
    tail -n 60 "$log"
    exit 1
fi

pass="$(sed -E 's/.*pass=([0-9]+).*/\1/' <<< "$done_line")"
fail="$(sed -E 's/.*fail=([0-9]+).*/\1/' <<< "$done_line")"
if [ "$fail" -ne 0 ] || [ "$pass" -eq 0 ]; then
    echo "::error::$task: $fail self-test check(s) failed, $pass passed"
    exit 1
fi
echo "$task: all $pass self-test checks passed"
