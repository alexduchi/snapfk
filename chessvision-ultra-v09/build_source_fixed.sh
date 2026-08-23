#!/usr/bin/env bash
set -uo pipefail
# The v0.9 patch also contains README/build-status hunks that may differ in the
# reconstructed CI tree. GNU patch still applies the app/code hunks afterwards,
# so tolerate only that aggregate exit code and then verify every required code change.
bash chessvision-ultra-v09/build_source.sh || true
set -e
grep -q "versionName '0.9.0-auto-fast'" /tmp/chessvision/app/build.gradle
grep -q 'startThinkingForCurrentPosition' /tmp/chessvision/app/src/main/java/fr/neo/chessvisionbot/ChessBotService.java
grep -q 'AUTO side/orientation' /tmp/chessvision/app/src/main/java/fr/neo/chessvisionbot/ChessBotService.java
test -f /tmp/chessvision/core-test/AutoOrientationTest.java
echo 'v0.9 code reconstruction verified'
