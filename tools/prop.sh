#!/usr/bin/env bash
# Shows or sets a launcher key in files/claudecraft.properties on the headset (read at the next launch).
#   tools/prop.sh                       print the file
#   tools/prop.sh immersivemc.install false
# Close the game first (adb shell am force-stop $PKG); the change applies when it is launched again.
set -euo pipefail
cd "$(dirname "$0")/.."
. tools/common.sh
f="$FILES/claudecraft.properties"
adb shell "[ -f $f ]" || { echo "$f not found: launch our build once so it writes the file" >&2; exit 1; }
if [ $# -eq 0 ]; then adb shell cat "$f" | tr -d '\r'; exit 0; fi
key="${1:?key}" value="${2:?value}"
tmp=$(mktemp)
adb pull "$f" "$tmp" >/dev/null
if grep -q -E "^${key//./\\.}=" "$tmp"; then
  sed -i.bak -E "s#^${key//./\\.}=.*#${key}=${value}#" "$tmp" && rm -f "$tmp.bak"
else
  printf '%s=%s\n' "$key" "$value" >> "$tmp"
fi
adb push "$tmp" "$f" >/dev/null
rm -f "$tmp"
adb shell cat "$f" | tr -d '\r' | grep -F "${key}="
