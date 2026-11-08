#!/usr/bin/env bash
# Builds the IBRA TV launcher and the self-test APK without Gradle:
# aapt (resources) → javac (Java 8 bytecode) → dx/d8 (dex) → zipalign → apksigner.
#
# Tools: on Ubuntu `apt-get install android-sdk-platform-23 aapt dalvik-exchange zipalign apksigner openjdk-17-jdk-headless`,
# or set ANDROID_JAR / BUILD_TOOLS to an Android SDK.
#
# Signing: set IBRA_KEYSTORE_B64 + IBRA_KEYSTORE_PASS (CI secret) to sign with the release key, so updates
# install over each other. Without them a local key is created in android/.keys (git-ignored).
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
OUT="${OUT:-$PWD/out}"
mkdir -p "$OUT" .keys

find_tool() { # name...
  for n in "$@"; do
    if [ -n "${BUILD_TOOLS:-}" ] && [ -x "$BUILD_TOOLS/$n" ]; then echo "$BUILD_TOOLS/$n"; return; fi
    if command -v "$n" >/dev/null 2>&1; then command -v "$n"; return; fi
  done
  echo "missing tool: $*" >&2; exit 1
}
AAPT=$(find_tool aapt)
ZIPALIGN=$(find_tool zipalign)
APKSIGNER=$(find_tool apksigner)
DEXER=$(find_tool d8 dalvik-exchange dx)

KS=.keys/ibra.jks
if [ -n "${IBRA_KEYSTORE_B64:-}" ]; then
  echo "$IBRA_KEYSTORE_B64" | base64 -d > "$KS"
  KS_PASS="${IBRA_KEYSTORE_PASS:?IBRA_KEYSTORE_PASS required}"
else
  KS_PASS="${IBRA_KEYSTORE_PASS:-ibra-dev-key}"
  if [ ! -f "$KS" ]; then
    keytool -genkeypair -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" -alias ibra \
      -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=IBRA TV Tuner (dev), O=IBRAPROGRAM" >/dev/null 2>&1
  fi
fi

build() { # dir apkname [assets-dir]
  local dir="$1" name="$2" assets="${3:-}" work
  work=$(mktemp -d)
  mkdir -p "$work/gen" "$work/classes"
  local vname="1.0.${VERSION_CODE:-1}"
  "$AAPT" package -f -m -J "$work/gen" -M "$dir/AndroidManifest.xml" -S "$dir/res" -I "$ANDROID_JAR" \
    ${assets:+-A "$assets" -0 apk} -F "$work/res.apk" \
    --replace-version --version-code "${VERSION_CODE:-1}" --version-name "${VERSION_NAME:-$vname}"
  javac -nowarn -encoding UTF-8 -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -cp "$ANDROID_JAR" \
    -d "$work/classes" $(find "$dir/src" "$work/gen" $([ "$dir" != testapp ] && echo common/src) -name '*.java') 2>&1 | grep -v 'bootstrap class path\|^warning\|^1 warning\|source value 8\|target value 8\|To suppress warnings' || true
  [ -n "$(find "$work/classes" -name '*.class' | head -1)" ] || { echo "javac failed for $dir" >&2; exit 1; }
  case "$(basename "$DEXER")" in
    d8) "$DEXER" --release --min-api 21 --lib "$ANDROID_JAR" --output "$work" $(find "$work/classes" -name '*.class') ;;
    *)  "$DEXER" --dex --min-sdk-version=21 --output="$work/classes.dex" "$work/classes" ;;
  esac
  cp "$work/res.apk" "$work/unsigned.apk"
  (cd "$work" && "$AAPT" add unsigned.apk classes.dex >/dev/null)
  "$ZIPALIGN" -f -p 4 "$work/unsigned.apk" "$work/aligned.apk"
  "$APKSIGNER" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" --ks-key-alias ibra \
    --min-sdk-version 21 --out "$OUT/$name" "$work/aligned.apk"
  "$APKSIGNER" verify "$OUT/$name"
  rm -rf "$work"
  echo "built $OUT/$name ($(du -k "$OUT/$name" | cut -f1) KB)"
}

build launcher ibra-tv-launcher.apk launcher/assets
build testapp ibra-tv-testapp.apk

# Phone installer carries the launcher it just built (stored uncompressed so it can be streamed).
mkdir -p installer/assets
cp "$OUT/ibra-tv-launcher.apk" installer/assets/ibra-home.apk
build installer ibra-home-installer.apk installer/assets
