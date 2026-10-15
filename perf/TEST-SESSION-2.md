# Test session 2: Vivecraft 1.3.15 on the headset (`1.3.15-mihail.0`, then `1.3.15-mihail.2`)

Total time: about 40 minutes. Same setup as session 1 (Mac, `adb`, repo root, headset plugged in).
This session answers one question first: **does Vivecraft 1.3.15 with QuestCraft's OpenXR code run on
the Quest at all?** Only then do the memory numbers matter. If anything crashes, run step 0 before
relaunching.

Both builds use the side-by-side package (`QuestCraft CC`), so the official app and your worlds stay
untouched. The APKs are in the GitHub Actions artifacts `questcraft-claudecraft-apk` of the runs for
`perf/vivecraft-1.3` (mihail.0) and `integration/mihail.2` (mihail.2). Or push the tag `mihail.2` from
the Mac to get a release with both.

## 0. After any crash (2 min)

```
PKG=com.qcxr.qcxr.claudecraft tools/forensics.sh
```

## 1. Base build without our changes: `1.3.15-mihail.0` (10 min)

1. `tools/install.sh ~/Downloads/questcraft-claudecraft-1.3.15-mihail.0-side.apk`
   (it installs over the `1.2.5-mihail.1` side build and keeps its data).
2. Open QuestCraft CC and press Play. Check, and tell me yes/no for each:
   - VR starts (no "VR initialisation failed" screen);
   - both controllers track; trigger, grip, thumbsticks and the menu button work;
   - the title screen and the menu world look right in both eyes (nothing mirrored, shifted or black);
   - the keyboard and the radial menu open.
3. Join your usual world and play for 5 minutes: walk, break and place blocks, open your inventory and
   a chest, and turn your head quickly.
4. `PKG=com.qcxr.qcxr.claudecraft tools/logs.sh`

Stop here if VR does not start or anything above is broken, and send me the logs.

## 2. Our build: `1.3.15-mihail.2` (25 min)

1. `tools/install.sh ~/Downloads/questcraft-claudecraft-1.3.15-mihail.2-side.apk`
2. Same routine as session 1, step 4, with `PKG=com.qcxr.qcxr.claudecraft`:
   - menu before Play: `mem.sh launcher`; title screen: `mem.sh title`;
   - join: `load` at ~10 s, `settled` at 60 s, `walk` after 2 min of walking;
   - play 15 minutes: `mem.sh long`.
3. `files/latestlog.txt` should say `installed bundled Vivecraft 1.3.15-mihail.2`. In the game, VR
   Settings → Performance and Stability should list every toggle; "Stream Menu World Load" is gone (on
   1.3.15, upstream fixed that problem its own way).
4. `PKG=com.qcxr.qcxr.claudecraft tools/logs.sh`, plus `tools/metrics.sh` if OVR Metrics was recording.
