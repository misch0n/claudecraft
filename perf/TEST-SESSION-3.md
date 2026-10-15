# Test session 3: shared eye culling and ImmersiveMC (`1.3.15-mihail.3`)

Total time: about 45 minutes. Same setup as before (Mac, `adb`, repo root, headset plugged in). If
anything crashes, run step 0 before relaunching.

The build adds two things on top of `mihail.2`, both on by default:
- **Shared Eye Culling**: the right eye reuses the visible-chunk lists the left eye computed, instead of
  working them out again. It saves CPU time per frame. The risk is chunks missing or flickering at the
  edge of one eye.
- **ImmersiveMC**: you use chests, furnaces, crafting tables and the like with your hands. Our APK
  installs it; QuestCraft's own mod list doesn't have it.

Get the side APK from the artifact `questcraft-claudecraft-apk` of Actions run
[38765161759](https://github.com/misch0n/claudecraft/actions/runs/38765161759) (Summary page, scroll to
Artifacts), or push the tag `mihail.3` from the Mac to get a release.

## 0. After any crash (2 min)

```
PKG=com.qcxr.qcxr.claudecraft tools/forensics.sh
```

## 1. Install and check the build (5 min)

1. `tools/install.sh ~/Downloads/questcraft-claudecraft-1.3.15-mihail.3-side.apk` (installs over the
   previous side build and keeps its data).
2. Open QuestCraft CC. On its menu, before pressing Play: `PKG=com.qcxr.qcxr.claudecraft tools/mem.sh launcher`.
3. Press Play. Title screen: `PKG=com.qcxr.qcxr.claudecraft tools/mem.sh title`.
4. In the game, VR Settings → Performance and Stability should show **Shared Eye Culling** on.

## 2. Shared eye culling: does anything look wrong? (10 min)

Join your usual world. Then, with **one eye closed at a time** (left, then right):
1. Turn your head quickly left and right, and up and down.
2. Stand near a cliff edge or a wall and look along it, then past it into the distance.
3. Walk through a cave or a village.

Tell me: did chunks or blocks ever pop in late, go missing or flicker **at the edge of your view**, in
one eye but not the other? Note which eye and roughly where.

## 3. Shared eye culling on vs off (10 min)

Start OVR Metrics recording if you use it. Walk the same loop (about 2 minutes) twice:
1. Shared Eye Culling **on**: walk the loop.
2. VR Settings → Performance and Stability → Shared Eye Culling **off**, then walk the same loop again.
3. Turn it back **on**.

The game logs a `[VCPERF] cull` line every 30 s with the culling time per eye, so I can compare the
two loops from the log. Tell me if one felt smoother.

## 4. ImmersiveMC (5 min)

Try each, and tell me works / doesn't / didn't try:
1. Open a chest by grabbing and lifting its lid.
2. Put items into a furnace or a crafting table with your hand.
3. Reach over your shoulder to open the backpack (your inventory as a bag).

On the desktop test run, ImmersiveMC logged an error about registering its chest-lid/backpack
interactions twice. The game kept running, and I'm investigating it. If the chest lid or the backpack
doesn't work, that's probably the cause.

## 5. Memory, same routine as before (10 min)

1. Join, then `load` at ~10 s, `settled` at 60 s, `walk` after 2 min of walking
   (`PKG=com.qcxr.qcxr.claudecraft tools/mem.sh <label>` each time).
2. Play normally for 10 minutes, then `mem.sh long`.

## 6. If you have time: ImmersiveMC off (5 min)

1. Quit the game, then `PKG=com.qcxr.qcxr.claudecraft tools/prop.sh immersivemc.install false`.
2. Start it again, join, and take `mem.sh settled-noimc` at 60 s.
3. Turn it back on: `PKG=com.qcxr.qcxr.claudecraft tools/prop.sh immersivemc.install true`.

## 7. Pull everything

```
PKG=com.qcxr.qcxr.claudecraft tools/logs.sh
tools/metrics.sh
```

## What I do with it

- I compare the `[VCPERF] cull` lines from step 3 (left eye time, right eye skipped or not, how often
  it fell back to per-eye culling).
- I compare the memory snapshots with sessions 1 and 2, and ImmersiveMC on vs off.
- Your answers in steps 2 and 4 decide whether Shared Eye Culling and ImmersiveMC stay on by default.
