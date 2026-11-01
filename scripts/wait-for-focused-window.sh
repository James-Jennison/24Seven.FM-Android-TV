#!/usr/bin/env bash
# Withhold acceptance input until the expected app window has input focus.
#
# Input injected before the first window is focusable times out after about five seconds
# ("Application does not have a focused window"). Debug cold launches on the Google TV
# Streamer take 5-7.5 seconds, so acceptance scripts must wait instead of sending keys
# right after `am start`.
#
# Usage:
#   scripts/wait-for-focused-window.sh --device <adb-serial> --package <id> \
#       [--activity <class>] [--expect-model <ro.product.model>] [--timeout <seconds>] \
#       [-- <command to run once focused>]
#
# Exit codes: 0 focused (and the trailing command's status, if given), 2 usage error,
# 3 wrong device, 4 timeout waiting for focus, 5 adb unavailable or device offline.
set -euo pipefail

usage() { sed -n '2,15p' "$0" >&2; exit 2; }
fail() { local code=$1; shift; printf 'focus-wait=failed: %s\n' "$*" >&2; exit "$code"; }

device= package= activity= expect_model= timeout=30
while (($#)); do
  case $1 in
    --device) device=${2:-}; shift 2 ;;
    --package) package=${2:-}; shift 2 ;;
    --activity) activity=${2:-}; shift 2 ;;
    --expect-model) expect_model=${2:-}; shift 2 ;;
    --timeout) timeout=${2:-}; shift 2 ;;
    --) shift; break ;;
    -h|--help) usage ;;
    *) printf 'unknown argument: %s\n' "$1" >&2; usage ;;
  esac
done

[[ -n $device && -n $package ]] || usage
[[ $package =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$ ]] || fail 2 "invalid package: $package"
[[ -z $activity || $activity =~ ^[A-Za-z0-9_.$]+$ ]] || fail 2 "invalid activity: $activity"
[[ $timeout =~ ^[1-9][0-9]*$ ]] || fail 2 "timeout must be a positive integer number of seconds"

command -v adb >/dev/null 2>&1 || fail 5 'adb not found'

if [[ -n $expect_model ]]; then
  model=$(adb -s "$device" shell getprop ro.product.model 2>/dev/null | tr -d '\r') \
    || fail 5 "cannot read model from $device"
  [[ $model == "$expect_model" ]] || fail 3 "device $device is '$model', expected '$expect_model'"
fi

deadline=$((SECONDS + timeout))
while :; do
  focus=$(adb -s "$device" shell dumpsys window 2>/dev/null | tr -d '\r' \
    | sed -n 's/^[[:space:]]*mCurrentFocus=Window{[^ ]* [^ ]* \([^ }]*\)}.*/\1/p' | head -n 1) || focus=
  if [[ -n $focus ]]; then
    focused_package=${focus%%/*}
    focused_activity=${focus#*/}
    if [[ $focused_package == "$package" && ( -z $activity || $focused_activity == "$activity" ) ]]; then
      printf 'focus-wait=ready: %s\n' "$focus" >&2
      break
    fi
  fi
  ((SECONDS < deadline)) || fail 4 "no focus for $package${activity:+/$activity} on $device within ${timeout}s (last focus: ${focus:-none})"
  sleep 0.5
done

(($#)) && exec "$@"
exit 0
