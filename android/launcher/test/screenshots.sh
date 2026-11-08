#!/usr/bin/env bash
# Runs inside the Android TV emulator job: installs IBRA TV HOME, makes it Home, and screenshots the screens
# with live data (weather, posters, prayer times). Output: shots/*.png + logcat + a crash report.
set -u
OUT=shots
mkdir -p "$OUT"
A="adb"
$A wait-for-device
for i in $(seq 1 60); do [ "$($A shell getprop sys.boot_completed | tr -d '\r')" = "1" ] && break; sleep 3; done
$A shell input keyevent KEYCODE_WAKEUP
$A shell settings put system screen_off_timeout 1800000
$A logcat -c
$A install -r -g android/out/ibra-tv-launcher.apk
$A shell am broadcast -n tv.ibra.tuner.launcher/.ConfigReceiver -a tv.ibra.tuner.APPLY_CONFIG --es cfg "$(printf '%s' '{"updateMode":"off"}' | base64 -w0)"
$A shell cmd package set-home-activity tv.ibra.tuner.launcher/.HomeActivity
$A shell input keyevent KEYCODE_HOME
sleep 3
$A shell am start -n tv.ibra.tuner.launcher/.HomeActivity
shot() { sleep "${2:-8}"; $A exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }
cfg() { $A shell am broadcast -n tv.ibra.tuner.launcher/.ConfigReceiver -a tv.ibra.tuner.APPLY_CONFIG --es cfg "$(printf '%s' "$1" | base64 -w0)" ${2:+--es open $2} >/dev/null; }
key() { $A shell input keyevent "$@"; }

shot 01-home-series 45
shot 01b-home-later 20
cfg '{"contentCat":"anime"}';                         shot 02-home-anime 20
cfg '{"contentCat":"football"}';                      shot 04-home-football 15
cfg '{"contentCat":"series"}'; sleep 3
n=10
for p in ibra cinema luxury simple metro neon calm ocean emerald sunset rose paper; do
  cfg "{\"applyPreset\":\"$p\"}"; n=$((n+1)); shot "$n-style-$p" 9
done
cfg '{"applyPreset":"cinema","lang":"en"}';           shot 30-cinema-english 8
cfg '{"applyPreset":"metro","lang":"ar"}' apps;      shot 31-all-apps-metro 6
key KEYCODE_BACK
cfg '{"applyPreset":"luxury"}' apps;                  shot 32-all-apps-circle 6
key KEYCODE_BACK
cfg '{"applyPreset":"ibra"}'; sleep 2
# dropdown: focus the category button and open it
cfg '{}' home; sleep 3
key KEYCODE_DPAD_UP; sleep 1; key KEYCODE_DPAD_UP; sleep 1
$A shell dumpsys window | grep -i mCurrentFocus | head -1 > "$OUT/focus.txt"
cfg '{}' settings;                                    shot 40-settings 6
key KEYCODE_DPAD_CENTER;                              shot 41-settings-preset-dialog 4
key KEYCODE_BACK; key KEYCODE_BACK
cfg '{}' apps;                                        shot 42-all-apps 6
key KEYCODE_BACK
cfg '{}' search;                                      shot 43-search 6
$A shell input text "you"; shot 44-search-results 4
key KEYCODE_BACK; key KEYCODE_BACK
cfg '{}' home; sleep 2
key KEYCODE_DPAD_CENTER; shot 45-after-ok 5

$A logcat -d > "$OUT/logcat.txt"
grep -E " IBRA |FATAL" "$OUT/logcat.txt" > "$OUT/ibra-log.txt" || true
grep -E "FATAL EXCEPTION|AndroidRuntime" -A 25 "$OUT/logcat.txt" | grep -B2 -A25 "tv.ibra" > "$OUT/crashes.txt" || true
if [ -s "$OUT/crashes.txt" ]; then echo "CRASHES FOUND"; head -60 "$OUT/crashes.txt"; else echo "no crashes" > "$OUT/crashes.txt"; fi
$A shell dumpsys meminfo tv.ibra.tuner.launcher | head -40 > "$OUT/meminfo.txt"
exit 0
