#!/usr/bin/env bash
# Puts the original Vivecraft.jar back (from the backup deploy.sh made).
set -euo pipefail
cd "$(dirname "$0")/.."
. tools/common.sh
inst=$(instance_dir); target="$inst/mods/Vivecraft.jar"
adb shell "[ -f $target.orig ] && cp $target.orig $target && echo restored $target"
