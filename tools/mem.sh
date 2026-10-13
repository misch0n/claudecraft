#!/usr/bin/env bash
# Memory + per-thread CPU snapshot:  tools/mem.sh <label>   (title, load, settled, walk, long)
set -euo pipefail
cd "$(dirname "$0")/.."
. tools/common.sh
label="${1:?usage: tools/mem.sh <label>}"
pid=$(adb shell pidof "$PKG" | tr -d '\r')
[ -n "$pid" ] || { echo "$PKG is not running" >&2; exit 1; }
adb shell dumpsys meminfo "$PKG" > "$RUN/mem-$label.txt"
adb shell top -H -b -n 1 -p "$pid" > "$RUN/threads-$label.txt" 2>&1 || true
adb shell dumpsys gpu --gpumem "$pid" > "$RUN/gpumem-$label.txt" 2>&1 || true
echo "$label: $(grep -E 'TOTAL (PSS|RSS):|TOTAL ' "$RUN/mem-$label.txt" | head -2 | tr -s ' ' | tr '\n' ' ')"
grep -E '^ *(Java Heap|Native Heap|Graphics|Private Other|System):' "$RUN/mem-$label.txt" || true
echo "busiest threads:"; awk '/PID/{f=1} f&&c<13{print;c++}' "$RUN/threads-$label.txt"
