#!/usr/bin/env bash
# Turns a JVM GC log on/off for the OFFICIAL app via custom_env.txt (our build has its own setting).
#   tools/gclog.sh on | off
# Pojlib crashes on blank or "="-less lines in custom_env.txt, so the file is rewritten cleanly.
set -euo pipefail
cd "$(dirname "$0")/.."
. tools/common.sh
mode="${1:?usage: tools/gclog.sh on|off}"
tmp="$(mktemp)"
adb pull "$FILES/custom_env.txt" "$tmp" >/dev/null 2>&1 || : > "$tmp"
grep -v -E '^JAVA_TOOL_OPTIONS=-Xlog:gc' "$tmp" | grep '=' > "$tmp.new" || true
if [ "$mode" = on ]; then
  echo "JAVA_TOOL_OPTIONS=-Xlog:gc*:file=$FILES/gc.log:uptime,level,tags:filecount=3,filesize=10M" >> "$tmp.new"
fi
if [ -s "$tmp.new" ]; then adb push "$tmp.new" "$FILES/custom_env.txt" >/dev/null; else adb shell rm -f "$FILES/custom_env.txt"; fi
echo "custom_env.txt now:"; cat "$tmp.new" 2>/dev/null || true
rm -f "$tmp" "$tmp.new"
