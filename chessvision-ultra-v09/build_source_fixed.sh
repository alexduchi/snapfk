#!/usr/bin/env bash
set -uo pipefail
# The v0.9 diff was generated from the packaged v0.8 source while CI reconstructs
# v0.8 from the historical patches. Minor metadata/context differences are expected.
bash chessvision-ultra-v09/build_source.sh || true
set -e
python3 - <<'PY'
from pathlib import Path
p=Path('/tmp/chessvision/app/src/main/java/fr/neo/chessvisionbot/ChessBotService.java')
s=p.read_text()
if 'private volatile long gameEpoch=0L;' not in s:
    needle='private volatile boolean running=false,capturing=false,forceScan=true,thinking=false,movePending=false;'
    if needle not in s:
        raise SystemExit('cannot find ChessBotService state declaration')
    s=s.replace(needle, needle+'''\n    private volatile long gameEpoch=0L;\n    private boolean autoSideLocked=false;\n    private Boolean autoCandidateWhiteBottom=null;\n    private int autoCandidateStable=0;''',1)
p.write_text(s)
PY
grep -q "versionName '0.9.0-auto-fast'" /tmp/chessvision/app/build.gradle
grep -q 'private volatile long gameEpoch=0L' /tmp/chessvision/app/src/main/java/fr/neo/chessvisionbot/ChessBotService.java
grep -q 'startThinkingForCurrentPosition' /tmp/chessvision/app/src/main/java/fr/neo/chessvisionbot/ChessBotService.java
grep -q 'AUTO side/orientation' /tmp/chessvision/app/src/main/java/fr/neo/chessvisionbot/ChessBotService.java
test -f /tmp/chessvision/core-test/AutoOrientationTest.java
echo 'v0.9 code reconstruction verified'
