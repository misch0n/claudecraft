# Shared settings for the device scripts. Source it; don't run it.
# Run the scripts from the repo root on the Mac with the headset connected over USB (adb).

# Which app: official QuestCraft (com.qcxr.qcxr) or our side-by-side build (com.qcxr.qcxr.claudecraft).
PKG="${PKG:-com.qcxr.qcxr}"
FILES="/sdcard/Android/data/$PKG/files"
# Instance folder: QuestCraft names it after the instance; override with INSTANCE=... if needed.
INSTANCE="${INSTANCE:-}"

command -v adb >/dev/null || { echo "adb not found (install Android platform-tools)" >&2; exit 1; }
adb get-state >/dev/null 2>&1 || { echo "no device: plug in the headset and allow USB debugging" >&2; exit 1; }

# Current run folder: perf/runs/<timestamp>/ (git-ignored). Reused for 30 minutes so one session's
# files land together; set RUN=... to force a folder.
if [ -z "${RUN:-}" ]; then
  last=$(ls -1d perf/runs/*/ 2>/dev/null | tail -1)
  if [ -n "$last" ] && [ $(( $(date +%s) - $(stat -c %Y "$last" 2>/dev/null || stat -f %m "$last") )) -lt 1800 ]; then
    RUN="${last%/}"
  else
    RUN="perf/runs/$(date +%Y%m%d-%H%M%S)"
  fi
fi
mkdir -p "$RUN"

# Finds the instance directory (the one containing mods/) under $FILES.
instance_dir() {
  if [ -n "$INSTANCE" ]; then echo "$FILES/instances/$INSTANCE"; return; fi
  adb shell "ls -d $FILES/instances/*/mods 2>/dev/null" | tr -d '\r' | head -1 | sed 's#/mods$##'
}

# Redacts the Minecraft access token (and similar secrets) from a file in place.
redact() {
  sed -E -i.bak 's/(--accessToken[ ,]+)[^ ,]+/\1<redacted>/g; s/(accessToken"?[:=] *"?)[A-Za-z0-9._-]{20,}/\1<redacted>/g' "$1" && rm -f "$1.bak"
}
