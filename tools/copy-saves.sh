#!/usr/bin/env bash
# Copies worlds from the official app's instance to the side-by-side build's instance (on the headset).
#   tools/copy-saves.sh <instance-name>   (create the instance in the CC launcher first)
set -euo pipefail
cd "$(dirname "$0")/.."
name="${1:?usage: tools/copy-saves.sh <instance-name, lowercase with _ for spaces>}"
src="/sdcard/Android/data/com.qcxr.qcxr/files/instances/$name/saves"
dst="/sdcard/Android/data/com.qcxr.qcxr.claudecraft/files/instances/$name/saves"
adb shell "mkdir -p $dst && cp -r $src/. $dst/ && ls $dst"
