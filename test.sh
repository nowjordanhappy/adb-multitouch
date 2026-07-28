#!/usr/bin/env bash
# Unit-test the gesture geometry (pure JVM, no device, no android.jar needed).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
javac -d "$tmp" "$HERE/Gestures.java" "$HERE/GesturesTest.java"
java -ea -cp "$tmp" com.strux.mt.GesturesTest
