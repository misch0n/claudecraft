# CLAUDE.md — QuestCraft Vivecraft performance fork

Personal-use fork of **QuestCraftPlusPlus/VivecraftMod** (LGPLv3), the VR mod inside QuestCraft
(Minecraft: Java Edition running natively on a Meta Quest 3S). Goal: make it stable and smooth
on the headset. Never commit Minecraft/Mojang code or jars. Not for redistribution.

Read `PLAN.md` for the phased work. This file is standing context and rules.

## The problem we are solving

- **Memory:** the QuestCraft process peaks around **6.3 GB** on an **8 GB unified-memory**
  device. The Java heap is capped at **1.8 GB**, so ~4.5 GB is off-heap: native, GPU
  (textures, vertex buffers and framebuffers all count as system RAM here), JVM metaspace/JIT,
  and possibly the Unity launcher. Android's low-memory killer ends the game.
- **Load stutter:** joining a world stutters and sometimes crashes; once loaded it runs fine
  at render distance 4–6.
- **CPU per frame:** Minecraft is draw-call/CPU bound, and the world render runs twice per frame
  (one per eye).

## Hardware and runtime facts

- Quest 3S: Snapdragon XR2 Gen 2, 8 GB shared RAM, default eye buffer ~1680×1760, refresh 72/90/120 Hz.
- Stack: Unity launcher (QCXR-XR-Wrapper) → Pojlib → OpenJDK on Android → Fabric → Minecraft +
  this mod + Sodium, Lithium, FerriteCore, ModernFix, etc. → "Light Thin Wrapper" (desktop GL →
  GL ES) → OpenXR with the **OpenGL ES** graphics binding.
- Android package / data dir: `com.qcxr.qcxr`, `/sdcard/Android/data/com.qcxr.qcxr/`.
  **Verify the exact instance and mods paths on device before relying on them.**

## Code map (where the relevant logic lives)

All under `common/src/main/java/org/vivecraft/`:

| Area | File |
|---|---|
| Per-frame pass loop; each eye calls the full `gameRenderer.render` | `client_vr/render/helpers/VRPassHelper.java` |
| Which passes run; all framebuffer allocation | `client_vr/provider/VRRenderer.java` (`getRenderPasses`, `setupRenderConfiguration`) |
| OpenXR session, swapchains, refresh rate (`initDisplayRefreshRate` picks the **max**) | `client_vr/provider/openxr/MCOpenXR.java` |
| Swapchain images, frame submit | `client_vr/provider/openxr/OpenXRStereoRenderer.java` |
| Android glue (QuestCraft-specific) | `client_vr/provider/openxr/DeviceCompat.java`, `util/VLoader.java` |
| Forces Sodium chunk-graph rebuild **every pass** | `mod_compat_vr/sodium/mixin/SodiumWorldRendererVRMixin.java` |
| Vanilla equivalent (no Sodium) | `mixin/client_vr/renderer/NoSodiumLevelRendererVRMixin.java` |
| Second-eye dedup today (lighting only) via `isFirstPass` | `mixin/client_vr/renderer/LevelRendererVRMixin.java` |
| Raycast done once (`vivecraft$onlyOnePick`) | `mixin/client_vr/renderer/GameRendererVRMixin.java` |
| Title-screen 3D world, never freed on world join | `client_vr/menuworlds/MenuWorldRenderer.java` |
| All user settings | `client_vr/settings/VRSettings.java` |
| Desktop provider with no headset (useful for CPU profiling on the Mac) | `client_vr/provider/nullvr/` |

## Rules

1. **Every change goes behind its own `VRSettings` toggle**, visible in the VR settings GUI.
   Default = original behaviour unless `PLAN.md` says otherwise. One build must allow A/B
   testing in a single headset session.
2. **Log the config.** On VR init, log one line prefixed `[VCPERF]` with the build tag and every
   perf toggle's value, so any log identifies what was tested.
3. **Build tag:** set `mod_version` in `gradle.properties` to `1.3.4-mihail.N` and bump N for every
   build deployed to the headset.
4. **Minimal, rebase-friendly diffs.** Prefer Mixins and small hooks over rewrites. Do not reformat
   files. Keep upstream rebases cheap.
5. **No new dependencies.**
6. **Never claim a win without data.** Every perf change gets a before/after entry in
   `perf/RESULTS.md` from the measurement protocol below. "Builds" and "runs" are not results.
7. **The human wears the headset.** Before each test session, write a short numbered test script
   for the user (what to do, for how long). After it, pull data and analyse it yourself.
8. **Small branches:** one branch per task (`perf/refresh-rate`, `perf/menu-world`, …), merged
   into `main` once measured.

## Build

- JDK **21** (`options.release = 21`), Fabric only (`enabled_platforms=fabric`).
- `./gradlew :fabric:build` → jar in `fabric/build/libs/` (expect `vivecraft-1.21.5-<mod_version>-fabric.jar`).
- Branch per Minecraft version: `OpenXR-1.21.5`, `OpenXR-1.21.8`, `OpenXR-1.21.10`, … — must match
  the version the QuestCraft instance runs.

## Device loop (scripts live in `tools/`)

Create these in Phase 0 and use them; don't type ad-hoc adb commands repeatedly.

- `tools/deploy.sh` — build, back up the on-device jar once (`Vivecraft.jar.orig`), push the
  new jar **under the same filename**, print the pushed `mod_version`.
- `tools/restore.sh` — put the original jar back.
- `tools/logs.sh` — pull `latest.log` (and crash reports if any) into `perf/runs/<timestamp>/`.
- `tools/mem.sh <label>` — `adb shell dumpsys meminfo com.qcxr.qcxr` into the current run
  folder, tagged with the label.
- `tools/metrics.sh` — pull OVR Metrics Tool CSV captures (verify the capture path on device).

The QuestCraft launcher re-downloads Vivecraft only when its online manifest version changes,
so a pushed jar survives relaunches until QuestCraft ships a new Vivecraft. After any QuestCraft
update, re-run `deploy.sh`.

## Measurement protocol

- A fixed test world (pregenerated, fixed seed), render distance 6, simulation distance minimum,
  same spawn spot.
- Memory snapshots: `title` (title screen), `load` (≈10 s into joining), `settled` (60 s
  after joining), `walk` (after 2 min walking).
- During play: OVR Metrics capture (CPU/GPU frame time, FPS, CPU/GPU level, memory).
- Record in `perf/RESULTS.md`: build tag, toggles, the four meminfo totals plus the Java Heap,
  Native Heap and Graphics rows, median and p95 frame time, crashes yes/no.
