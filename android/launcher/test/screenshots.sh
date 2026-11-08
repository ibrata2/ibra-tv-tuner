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
cfg '{"contentCat":"movies"}';                        shot 03-home-movies 20
cfg '{"contentCat":"football"}';                      shot 04-home-football 15
cfg '{"contentCat":"tv","prayerCombined":true}';     shot 05-home-tv-combined 20
cfg '{"theme":"glass_white","contentCat":"series","prayerCombined":false}'; shot 06-theme-white 8
cfg '{"theme":"red_black","background":"gradient"}';  shot 07-theme-red 8
cfg '{"theme":"purple_cinema","background":"animated"}'; shot 08-theme-purple 8
cfg '{"theme":"gold","background":"horizon","iconSize":2}'; shot 09-theme-gold-large 8
cfg '{"theme":"glass_black","lang":"en","iconSize":1}'; shot 10-english 8
cfg '{"lang":"ar"}';                                  shot 11-arabic-again 6
# dropdown: focus the category button and open it
cfg '{}' home; sleep 3
key KEYCODE_DPAD_UP; sleep 1; key KEYCODE_DPAD_UP; sleep 1
$A shell dumpsys window | grep -i mCurrentFocus | head -1 > "$OUT/focus.txt"
cfg '{}' settings;                                    shot 12-settings 6
key KEYCODE_DPAD_CENTER;                              shot 13-settings-theme-dialog 4
key KEYCODE_BACK; key KEYCODE_BACK
cfg '{}' apps;                                        shot 14-all-apps 6
key KEYCODE_BACK
cfg '{}' search;                                      shot 15-search 6
$A shell input text "you"; shot 16-search-results 4
key KEYCODE_BACK; key KEYCODE_BACK
cfg '{}' home; sleep 2
key KEYCODE_DPAD_CENTER; shot 17-after-ok 5

$A logcat -d > "$OUT/logcat.txt"
grep -E " IBRA |FATAL" "$OUT/logcat.txt" > "$OUT/ibra-log.txt" || true
grep -E "FATAL EXCEPTION|AndroidRuntime" -A 25 "$OUT/logcat.txt" | grep -B2 -A25 "tv.ibra" > "$OUT/crashes.txt" || true
if [ -s "$OUT/crashes.txt" ]; then echo "CRASHES FOUND"; head -60 "$OUT/crashes.txt"; else echo "no crashes" > "$OUT/crashes.txt"; fi
$A shell dumpsys meminfo tv.ibra.tuner.launcher | head -40 > "$OUT/meminfo.txt"
exit 0
