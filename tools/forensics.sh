#!/usr/bin/env bash
# Run FIRST after a crash, before relaunching (QuestCraft wipes its own log at start and clears logcat).
# Saves why the app died (Android exit records, crash dropbox) and every log into perf/runs/<ts>/.
#   tools/forensics.sh            (official app)    PKG=com.qcxr.qcxr.claudecraft tools/forensics.sh
set -euo pipefail
cd "$(dirname "$0")/.."
. tools/common.sh
out="$RUN/forensics-$(date +%H%M%S)"; mkdir -p "$out"

# Why did the process die? Reason (LOW_MEMORY, CRASH_NATIVE, SIGNALED, ...), time, PSS/RSS at death.
adb shell dumpsys activity exit-info "$PKG" > "$out/exit-info.txt" 2>&1 || true
# Native and Java crash records kept by Android (no root needed).
for tag in data_app_native_crash data_app_crash SYSTEM_TOMBSTONE data_app_anr; do
  adb shell dumpsys dropbox --print "$tag" > "$out/dropbox-$tag.txt" 2>&1 || true
done
adb logcat -d -b crash > "$out/logcat-crash.txt" 2>&1 || true
adb logcat -d > "$out/logcat.txt" 2>&1 || true
adb shell dumpsys meminfo > "$out/meminfo-system.txt" 2>&1 || true

# Launcher and game logs.
for f in latestlog.txt latestlog.prev.txt gc.log gc.log.0 gc.log.1 gc.log.2 claudecraft.properties custom_env.txt; do
  adb pull "$FILES/$f" "$out/" >/dev/null 2>&1 || true
done
inst=$(instance_dir)
if [ -n "$inst" ]; then
  adb pull "$inst/logs/latest.log" "$out/" >/dev/null 2>&1 || true
  adb pull "$inst/crash-reports" "$out/" >/dev/null 2>&1 || true
  for f in $(adb shell "ls $inst/hs_err_pid*.log 2>/dev/null" | tr -d '\r'); do adb pull "$f" "$out/" >/dev/null 2>&1 || true; done
fi
for f in "$out"/*.txt "$out"/*.log "$out"/crash-reports/*; do [ -f "$f" ] && redact "$f"; done

echo "saved to $out"
echo "--- last exit records ---"
grep -E 'ApplicationExitInfo|reason=|description=|pss=|rss=|timestamp=' "$out/exit-info.txt" | head -24 || true
