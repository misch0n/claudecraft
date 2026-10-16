#!/usr/bin/env bash
# Shows or sets Vivecraft settings (config/vivecraft-client-config.json in the instance) from the Mac, for when
# the game can't be seen. Close the game first; the change applies at the next start.
#   tools/vrset.sh                                   print our perf switches and their values
#   tools/vrset.sh xrSessionStateHandling false      set one
set -euo pipefail
cd "$(dirname "$0")/.."
. tools/common.sh
inst=$(instance_dir)
[ -n "$inst" ] || { echo "no instance found under $FILES/instances" >&2; exit 1; }
f="$inst/config/vivecraft-client-config.json"
adb shell "[ -f '$f' ]" || { echo "$f not found: start the game once first" >&2; exit 1; }
tmp=$(mktemp)
adb pull "$f" "$tmp" >/dev/null
if [ $# -eq 0 ]; then
  grep -E '"(perfMemoryLog|freeMenuWorldInGame|shareOutlineTargets|lazyAuxBuffers|fixSupporterLeak|clearTrackerRefs|xrStartupRetry|xrSessionStateHandling|xrLateFrameWait|xrRefreshRate|cacheInputOrigins|glErrorChecks|ltwLinearFilterFix|xrPerfHints|sharedEyeCulling|apiRegisterOnce|vrEnabled)"' "$tmp" | tr -d ' ,'
  rm -f "$tmp"; exit 0
fi
key="${1:?key}" value="${2:?value}"
grep -q "\"$key\"" "$tmp" || { echo "key $key not in $f" >&2; rm -f "$tmp"; exit 1; }
cp "$tmp" "$tmp.orig"
sed -E -i.bak "s/(\"$key\"[[:space:]]*:[[:space:]]*\")[^\"]*\"/\1$value\"/" "$tmp" && rm -f "$tmp.bak"
adb shell "cp '$f' '$f.bak'" >/dev/null
adb push "$tmp" "$f" >/dev/null
grep "\"$key\"" "$tmp"
rm -f "$tmp" "$tmp.orig"
