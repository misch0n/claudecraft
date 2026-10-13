#!/usr/bin/env bash
# Backs up worlds and launcher settings of an app to perf/backups/<pkg>-<ts>/ (not committed).
# Do this before uninstalling the official app (the "replace" APK needs that; uninstalling deletes its data).
set -euo pipefail
cd "$(dirname "$0")/.."
. tools/common.sh
dest="perf/backups/$PKG-$(date +%Y%m%d-%H%M%S)"; mkdir -p "$dest"
adb pull "$FILES/instances.json" "$dest/" >/dev/null 2>&1 || true
for inst in $(adb shell "ls -d $FILES/instances/*/" | tr -d '\r'); do
  name=$(basename "$inst"); mkdir -p "$dest/$name"
  for d in saves config options.txt screenshots; do adb pull "$inst/$d" "$dest/$name/" >/dev/null 2>&1 || true; done
done
du -sh "$dest"; echo "backup in $dest"
