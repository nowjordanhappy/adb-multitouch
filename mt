#!/usr/bin/env bash
# mt — multi-touch gestures over adb, no root. Wrapper around MultiTouch (app_process tool).
#
#   ./mt install                       push mt.jar to the device (once)
#   ./mt pinch  <cx> <cy> <g0> <g1> [steps] [ms]   two-finger pinch (g0->g1 = start/end finger gap, px)
#   ./mt pan    <cx> <cy> <dx> <dy>   [steps] [ms]   two-finger drag by (dx,dy)
#   ./mt tap    <x> <y>
#
# -s <serial> as the FIRST args to target a specific device: ./mt -s emulator-5556 pinch ...
set -euo pipefail

REMOTE=/data/local/tmp/mt.jar
CLASS=com.nowjordanhappy.mt.MultiTouch
HERE="$(cd "$(dirname "$0")" && pwd)"

ADB=(adb)
if [[ "${1:-}" == "-s" ]]; then
  [[ -n "${2:-}" ]] || { echo "-s needs a device serial" >&2; exit 1; }
  ADB=(adb -s "$2"); shift 2
fi

cmd="${1:-}"; shift || true
if [[ "$cmd" == "install" ]]; then
  "${ADB[@]}" push "$HERE/mt.jar" "$REMOTE" >/dev/null
  echo "pushed $REMOTE"
  exit 0
fi
# usage = the comment header, i.e. lines 2..first non-comment
[[ -n "$cmd" ]] || { awk 'NR>1 && !/^#/{exit} NR>1{sub(/^# ?/,""); print}' "$0"; exit 1; }

# auto-push if missing
"${ADB[@]}" shell "[ -f $REMOTE ]" || "${ADB[@]}" push "$HERE/mt.jar" "$REMOTE" >/dev/null
exec "${ADB[@]}" shell "CLASSPATH=$REMOTE app_process /system/bin $CLASS $cmd $*"
