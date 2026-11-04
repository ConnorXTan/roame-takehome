#!/bin/sh
# Runs the iOS UI suite including the failure-mode tests, which need a server per failure mode.
# Usage: scripts/ios-failure-tests.sh [simulator name]   (default: iPhone 17 Pro)
#
# Port 8080 serves the fixture, 8091 an empty list, 8092 HTTP 500 and 8093 malformed JSON. Port 8094
# stays down until the retry test has seen its error screen, and only then comes up.
set -eu
cd "$(dirname "$0")/.."
simulator="${1:-iPhone 17 Pro}"
results="${RESULTS_DIR:-build/ui-test-results}"
mkdir -p "$results"
rm -f "$results/start-late-server"
results="$(cd "$results" && pwd)"

# The same JDK lookup as the Xcode build phase, for shells with no JAVA_HOME and no java on the PATH.
if [ -z "${JAVA_HOME:-}" ] && ! /usr/libexec/java_home >/dev/null 2>&1; then
    for candidate in "/Applications/Android Studio.app/Contents/jbr/Contents/Home" /opt/homebrew/opt/openjdk@17 /opt/homebrew/opt/openjdk /usr/local/opt/openjdk@17; do
        if [ -x "$candidate/bin/java" ]; then export JAVA_HOME="$candidate"; break; fi
    done
fi

./gradlew -q :server:installDist
server=server/build/install/server/bin/server

pids=""
cleanup() { kill $pids 2>/dev/null || true; }
trap cleanup EXIT

start() { FLIGHTS_MODE="$1" PORT="$2" "$server" >"$results/server-$2.log" 2>&1 & pids="$pids $!"; }
start normal 8080
start empty 8091
start error 8092
start malformed 8093

# Every test depends on these, so stop now with the real cause rather than time out sixteen times.
for port in 8080 8091 8092 8093; do
    tries=0
    until curl -s -o /dev/null -m 1 "http://localhost:$port/flights"; do
        tries=$((tries + 1))
        if [ "$tries" -ge 30 ]; then
            echo "Server on port $port did not start:" >&2
            cat "$results/server-$port.log" >&2
            exit 1
        fi
        sleep 1
    done
done

( while [ ! -f "$results/start-late-server" ]; do sleep 1; done; FLIGHTS_MODE=normal PORT=8094 exec "$server" >"$results/server-8094.log" 2>&1 ) &
pids="$pids $!"

udid="$(xcrun simctl list devices available | grep -F "$simulator (" | head -1 | sed -E 's/.*\(([0-9A-F-]{36})\).*/\1/')"
# A previously installed test runner can hide newly added tests, so always start from a clean install.
xcrun simctl uninstall "$udid" travel.roame.takehome.ios.uitests.xctrunner 2>/dev/null || true
xcrun simctl uninstall "$udid" travel.roame.takehome.ios 2>/dev/null || true

TEST_RUNNER_SCREENSHOT_DIR="$results" TEST_RUNNER_FAILURE_SERVERS=1 xcodebuild test \
    -project iosApp/RoameStarter.xcodeproj -scheme RoameStarter \
    -destination "platform=iOS Simulator,id=$udid" CODE_SIGNING_ALLOWED=NO \
    ${DERIVED_DATA:+-derivedDataPath "$DERIVED_DATA"}
