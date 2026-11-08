#!/usr/bin/env bash
# Desktop-JVM tests for the launcher's pure-Java parts (prayer times, Hijri, next prayer).
set -euo pipefail
cd "$(dirname "$0")"
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
javac -nowarn -encoding UTF-8 -d "$W" ../src/tv/ibra/tuner/launcher/Prayer.java PrayerTest.java
java -Dfile.encoding=UTF-8 -cp "$W" PrayerTest
