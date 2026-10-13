#!/usr/bin/env bash
# Pulls OVR Metrics Tool CSV captures into the current run folder. Verify the path on your headset.
set -euo pipefail
cd "$(dirname "$0")/.."
. tools/common.sh
src="${OVR_METRICS_DIR:-/sdcard/Android/data/com.oculus.ovrmonitormetricsservice/files/CapturedMetrics}"
adb pull "$src" "$RUN/ovrmetrics" >/dev/null && ls -l "$RUN/ovrmetrics" || echo "nothing at $src (set OVR_METRICS_DIR)"
