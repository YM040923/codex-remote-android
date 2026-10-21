#!/usr/bin/env bash
# Capture the chat UI on the running emulator into design-refs/<set>/ and pull the PNGs.
# Usage: tools/capture-ui.sh <set-name>        e.g. tools/capture-ui.sh after-tokens
set -euo pipefail

set_name="${1:?usage: capture-ui.sh <set-name>}"
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
adb="E:/Android/Sdk/platform-tools/adb.exe"
device_dir="/storage/emulated/0/Android/data/com.codex.remote/files/baseline"
out_dir="$repo_root/../design-refs/captures/$set_name"

export ANDROID_HOME="E:/Android/Sdk"
"$adb" -s emulator-5554 shell "rm -rf $device_dir"

( cd "$repo_root" && ./gradlew --console=plain connectedDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=com.codex.remote.WorkspaceDeviceTest#captureBaselineLight,com.codex.remote.WorkspaceDeviceTest#captureBaselineDark" )

mkdir -p "$out_dir"
cd "$out_dir"
rm -f *.png
for name in $("$adb" -s emulator-5554 shell "ls $device_dir" | tr -d '\r'); do
  "$adb" -s emulator-5554 exec-out "cat $device_dir/$name" > "$name"
done
ls -l "$out_dir"
