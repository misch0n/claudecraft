# Test session 1: crash forensics, baseline, first build (`mihail.1`)

Total time: about 75 minutes. You need the Mac with `adb` (`brew install android-platform-tools`) and
this repo checked out. Run every command from the repo root with the headset plugged in. Each step
says how long it takes. If anything crashes, **do step 0 immediately** before relaunching.

Paste me the terminal output of each command (it is short), or just tell me "done": the files land in
`perf/runs/<timestamp>/`, and I'll ask for what I need.

## 0. After any crash (2 min)

```
tools/forensics.sh                               # official app
PKG=com.qcxr.qcxr.claudecraft tools/forensics.sh # our build
```

It records why Android killed the app (memory limit vs native crash) and saves every log, with your
access token redacted. Running it before relaunching matters: QuestCraft wipes its log on start.

## 1. Crash history (2 min, headset can stay off your head)

1. `tools/forensics.sh`. Android keeps records of the last crashes, so this tells us what killed the
   app in the sessions you already played.
2. `tools/apkinfo.sh`. Checks that your SideQuest install is the same APK we patch, and shows the heap
   size the launcher picked last time (`4096`, `2048` or `1536` MB, depending on free RAM at launch).

## 2. Baseline: official QuestCraft, unchanged (20 min)

1. Turn on the GC log for the official app (removable afterwards): `tools/gclog.sh on`
2. Start QuestCraft and Minecraft as usual. On the title screen: `tools/mem.sh title`.
3. Join your usual world. About 10 s in: `tools/mem.sh load`. At 60 s: `tools/mem.sh settled`.
4. Walk around for 2 minutes: `tools/mem.sh walk`.
5. Keep playing normally for 15 minutes (whatever you usually do): `tools/mem.sh long`.
6. Quit to the title screen, then `tools/logs.sh`.
7. Remove the GC log line again: `tools/gclog.sh off`

## 3. Install our build next to the official app (10 min)

1. Download `questcraft-claudecraft-1.2.5-mihail.1-side.apk` from the `mihail.1` release on GitHub
   (or the build artifact), then `tools/install.sh ~/Downloads/questcraft-claudecraft-1.2.5-mihail.1-side.apk`
   (or drag it into SideQuest).
2. Open **QuestCraft CC** from the library (Unknown Sources). Log in, create the same instance (same
   name, 1.21.5). The first start downloads Minecraft again; that's expected.
3. Copy your worlds over: `tools/copy-saves.sh <instance-name>` (lowercase, `_` for spaces).

## 4. Our build, default settings (25 min)

Same routine as step 2, with `PKG=com.qcxr.qcxr.claudecraft` in front of each command:

1. Start Minecraft from QuestCraft CC. Title screen: `PKG=com.qcxr.qcxr.claudecraft tools/mem.sh title`.
2. Join the world: `load` at ~10 s, `settled` at 60 s, `walk` after 2 min of walking.
3. Play 15–20 minutes: `long`.
4. Our launcher writes `files/claudecraft.properties` (every launcher switch, with comments) and logs what it
   did in `files/latestlog.txt`:
   - `Setting JVM memory to 2048MB (claudecraft policy ...)`;
   - `installed bundled Vivecraft 1.2.5-mihail.1`;
   - about 10 s after VR starts, `Unity player paused`.

   If the game misbehaves about 10 s after VR starts, set `unity.pauseInGame=false` in that file and
   tell me.
5. Note anything that looks different or wrong:
   - menus, keyboard, spyglass, camera;
   - smoothness;
   - whether the world view looks sharper or blurrier;
   - whether the GUI panels look smooth or pixelated.
6. Quit to the title screen and wait 10 s (the menu world should come back). Join again once
   (checks unload/reload).
7. `PKG=com.qcxr.qcxr.claudecraft tools/logs.sh`

## 5. If you have time: one A/B (10 min)

VR Settings → **Performance...** lists every change with an on/off switch. Turn off
**Free Menu World In Game**, rejoin the world, then `PKG=com.qcxr.qcxr.claudecraft tools/mem.sh settled-menuworld-on`.

## What I do with it

I read the exit records, the GC logs (allocation stalls during join), the `[VCPERF] mem` lines and the
meminfo snapshots. Then I fill `perf/RESULTS.md` for baseline vs `mihail.1` and tell you what helped,
what didn't, and what to try next.
