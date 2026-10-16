# Test: black screen after Minecraft starts (`1.3.15-mihail.4`)

Symptom: the launcher works, Minecraft loads, the view fades to black, menu music plays, nothing is visible.
About 20 minutes. Mac in the repo root, headset plugged in, `adb devices` shows it.

## 0. Setup (1 min)

```
git pull
export PKG=com.qcxr.qcxr.claudecraft
```

## 1. Reproduce once and save the logs (5 min)

1. Open QuestCraft CC, press Play, wait until the menu music has played for about 30 s.
2. Close it: `adb shell am force-stop $PKG`
3. Save everything (access token removed) and print the relevant lines:
   ```
   tools/forensics.sh
   grep -a -h -E 'VCPERF|OpenXR|xr startup|Vivecraft:|ERROR|Exception' "$(ls -td perf/runs/*/forensics-* | head -1)/latest.log" | tail -80
   ```
   **Paste the output to Claude (A).**
4. `tools/vrset.sh` and **paste its output (B)** (every switch and its current value).

## 2. Frame-handling switches off (5 min)

```
tools/vrset.sh xrSessionStateHandling false
tools/vrset.sh xrLateFrameWait false
tools/vrset.sh xrPerfHints false
```
Start the game again. **Tell Claude: picture or still black?** Then repeat step 1.2–1.3 and **paste (C)**.

## 3. Only if still black: every switch off (5 min)

```
for k in freeMenuWorldInGame shareOutlineTargets lazyAuxBuffers fixSupporterLeak clearTrackerRefs xrStartupRetry cacheInputOrigins ltwLinearFilterFix sharedEyeCulling apiRegisterOnce; do tools/vrset.sh $k false; done
```
Start the game again. **Picture or still black?** Then step 1.2–1.3 and **paste (D)**. Still black here means the cause is
the Vivecraft 1.3.15 base (merged with QuestCraft's OpenXR code), not one of our switches.

## 4. Put the switches back (1 min)

```
for k in xrSessionStateHandling xrLateFrameWait xrPerfHints freeMenuWorldInGame shareOutlineTargets lazyAuxBuffers fixSupporterLeak clearTrackerRefs xrStartupRetry cacheInputOrigins ltwLinearFilterFix sharedEyeCulling apiRegisterOnce; do tools/vrset.sh $k true; done
```
