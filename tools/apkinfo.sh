#!/usr/bin/env bash
# Which QuestCraft build is installed, and which heap size its launcher picked last time.
# The SideQuest copy may differ from the GitHub release APK we patch; this tells us.
#   tools/apkinfo.sh            (official app)    PKG=com.qcxr.qcxr.claudecraft tools/apkinfo.sh
set -euo pipefail
cd "$(dirname "$0")/.."
. tools/common.sh
# QCXR-6.0.0.apk from github.com/QuestCraftPlusPlus/QuestCraft/releases/tag/6.0.0 (versionCode 40)
RELEASE_SHA256=4d474dff57ba131a8719e073078c9212c739c18841716f37968dfee6732b783c

out="$RUN/apkinfo-$PKG.txt"
{
  adb shell dumpsys package "$PKG" | tr -d '\r' | grep -E 'versionCode=|versionName=|firstInstallTime=|lastUpdateTime=|installerPackageName=' | sed 's/^ *//' | sort -u
  apk=$(adb shell pm path "$PKG" | tr -d '\r' | sed -n 's/^package://p' | grep 'base.apk' | head -1)
  sha=$(adb shell sha256sum "$apk" | tr -d '\r' | awk '{print $1}')
  echo "base.apk sha256=$sha"
  if [ "$sha" = "$RELEASE_SHA256" ]; then
    echo "=> identical to the GitHub 6.0.0 release APK"
  else
    echo "=> NOT the GitHub 6.0.0 release APK (expected for our builds; for the official app, tell Claude)"
  fi
  echo "last heap choice: $(adb shell cat "$FILES/latestlog.txt" 2>/dev/null | tr -d '\r' | grep -o -E 'Setting JVM memory to [0-9]+ ?MB[^\"]*' | tail -1)"
} | tee "$out"
