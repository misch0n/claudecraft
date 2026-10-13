#!/usr/bin/env bash
# Installs a built APK.  tools/install.sh <apk>
#  - *-side.apk installs next to the official app (package com.qcxr.qcxr.claudecraft).
#  - *-replace.apk needs the official app uninstalled first (different signature), which DELETES its
#    data; run tools/backup-saves.sh first and pass --uninstall-official to confirm.
set -euo pipefail
cd "$(dirname "$0")/.."
apk="${1:?usage: tools/install.sh <apk> [--uninstall-official]}"
case "$apk" in
  *-side.apk) adb install -r "$apk" ;;
  *-replace.apk)
    if adb shell pm list packages | grep -q 'package:com.qcxr.qcxr$'; then
      [ "${2:-}" = --uninstall-official ] || { echo "official QuestCraft is installed; back up (tools/backup-saves.sh) and rerun with --uninstall-official" >&2; exit 1; }
      adb uninstall com.qcxr.qcxr
    fi
    adb install -r "$apk" ;;
  *) echo "expected a *-side.apk or *-replace.apk" >&2; exit 1 ;;
esac
