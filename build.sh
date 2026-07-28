#!/usr/bin/env bash
# Build mt.jar from MultiTouch.java. Needs the Android SDK (android.jar + d8).
# ANDROID_HOME or ANDROID_SDK_ROOT must point at the SDK; override PLATFORM / BUILD_TOOLS if needed.
set -euo pipefail

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
PLATFORM="${PLATFORM:-$(ls -d "$SDK"/platforms/android-* | sort -V | tail -1)}"
BUILD_TOOLS="${BUILD_TOOLS:-$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)}"
HERE="$(cd "$(dirname "$0")" && pwd)"

echo "sdk=$SDK"
echo "android.jar=$PLATFORM/android.jar"
echo "d8=$BUILD_TOOLS/d8"

rm -rf "$HERE/classes"
javac --release 11 -cp "$PLATFORM/android.jar" -d "$HERE/classes" "$HERE/MultiTouch.java" "$HERE/Gestures.java"
"$BUILD_TOOLS/d8" --min-api 26 --output "$HERE/mt.jar" "$HERE"/classes/com/nowjordanhappy/mt/*.class
rm -rf "$HERE/classes"
echo "built $HERE/mt.jar"
