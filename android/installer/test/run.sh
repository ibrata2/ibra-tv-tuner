#!/usr/bin/env bash
# End-to-end test of the phone installer's ADB client against a fake TV (no device needed).
# Scenarios: normal TV (install → reconnect without prompt → restore → remove) and a "stubborn"
# Google-TV-like device that only accepts a new Home after the stock launcher is disabled.
set -euo pipefail
cd "$(dirname "$0")"
APK="${1:-../../out/ibra-tv-launcher.apk}"
W=$(mktemp -d); trap 'rm -rf "$W"; kill $(jobs -p) 2>/dev/null || true' EXIT
javac -nowarn -d "$W/cls" ../src/tv/ibra/home/installer/{AdbClient,TvInstaller,Scanner}.java TestMain.java
run() { # port conns steps [stubborn]
  rm -rf "$W/keys"
  CONNS=$2 python3 -I fake_adbd.py "$1" "$APK" ${4:-} > "$W/srv.log" 2>&1 &
  sleep 1
  java -Dfile.encoding=UTF-8 -cp "$W/cls" TestMain "$1" "$APK" "$W/keys" "$3" > "$W/out.log" 2>&1 || { cat "$W/out.log" "$W/srv.log"; exit 1; }
  wait
  cat "$W/out.log"; cat "$W/srv.log"
}
check() { grep -q "$1" "$W/out.log" "$W/srv.log" || { echo "FAILED: expected '$1'"; exit 1; }; }

run 25555 3 install,restore,remove
check "install=true home=true"; check "restore=true"; check "'prompts': 1}"; check "'installed': False"
run 25556 2 install+disable,remove stubborn
check "install=true home=true"; check "'disabled': set()"
run 25557 1 install stubborn
check "install=true home=false"
echo "installer e2e: all scenarios passed"
