#!/usr/bin/env bash
# Pulls latest.log, the launcher log, the GC log and crash reports into the current run folder (redacted).
set -euo pipefail
cd "$(dirname "$0")/.."
. tools/common.sh
for f in latestlog.txt latestlog.prev.txt gc.log claudecraft.properties; do adb pull "$FILES/$f" "$RUN/" >/dev/null 2>&1 || true; done
inst=$(instance_dir)
[ -n "$inst" ] && { adb pull "$inst/logs/latest.log" "$RUN/" >/dev/null 2>&1 || true; adb pull "$inst/crash-reports" "$RUN/" >/dev/null 2>&1 || true; }
for f in "$RUN"/*.txt "$RUN"/*.log "$RUN"/crash-reports/*; do [ -f "$f" ] && redact "$f"; done
echo "saved to $RUN"
grep -h '\[VCPERF\]' "$RUN/latest.log" 2>/dev/null | cut -c1-300 | tail -12 || true
