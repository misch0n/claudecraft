#!/usr/bin/env bash
# Pushes a Vivecraft jar over the instance's Vivecraft.jar (backs up the original once).
#   tools/deploy.sh path/to/vivecraft-1.21.5-<version>-fabric.jar
# Note: our patched APK reinstalls its bundled jar at every launch. For manual jars, set
# vivecraft.installBundled=false in files/claudecraft.properties (or use the official app).
set -euo pipefail
cd "$(dirname "$0")/.."
. tools/common.sh
jar="${1:?usage: tools/deploy.sh <jar>}"
inst=$(instance_dir); [ -n "$inst" ] || { echo "no instance with a mods/ folder under $FILES/instances" >&2; exit 1; }
target="$inst/mods/Vivecraft.jar"
adb shell "[ -f $target.orig ] || cp $target $target.orig"
adb push "$jar" "$target" >/dev/null
echo "pushed $(unzip -p "$jar" fabric.mod.json | python3 -c 'import json,sys; print(json.load(sys.stdin)["version"])') to $target"
