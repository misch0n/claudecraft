# CLAUDE.md — QuestCraft performance fork

Personal-use fork of the QuestCraft stack (Minecraft: Java Edition running natively on a Meta Quest 3S).
It has two parts:
- the VR mod: upstream **Vivecraft 1.3.15** for 1.21.5 merged with the OpenXR/Android provider from the
  **QuestCraftPlusPlus/VivecraftMod** fork (LGPLv3);
- patches to QuestCraft's launcher core (**Pojlib**), shipped as a patched QuestCraft APK.

Goal: stop the crashes and make it smooth on the headset.
- Never commit Minecraft/Mojang code or jars, Unity's proprietary jars, QuestCraft APKs or raw logs.
- Commits go only to this repo; never push anywhere under QuestCraftPlusPlus or other upstreams.
- Not for redistribution.

Read `PLAN.md` for the phased work and `perf/FINDINGS.md` for the code audit behind it. This file
is standing context and rules.

## The problem we are solving

- **Crashes:** the whole QuestCraft app is killed when the process reaches about **6.3 GB** (the
  headset says so).
  - Launch crashes are more likely right after a previous crash; a 2nd or 3rd try usually works.
  - In-game crashes come after playing for a while.
- **One process holds everything:** the Unity launcher (Vulkan), Android's ART, OpenJDK 22 running
  Minecraft and the integrated server, LTW (desktop GL → GL ES), and Vivecraft's OpenXR session. GPU
  memory is ordinary RAM on this device.
- **The Java heap is not a fixed 1.8 GB.**
  - With the launcher's custom RAM setting off (the default), the 6.0.0 APK sets `-Xmx4096M` when more than
    4 GB is free at launch, `-Xmx2048M` from 2 to 4 GB and `-Xmx1536M` below, always with `-Xms1024M`.
    (Pojlib's later formula `max(0.8 × (free − threshold), 1536)` is not in the release; see
    `pojlib/IMPORTED.md`.) `latestlog.txt` says which: `QuestCraft: Setting JVM memory to N MB`.
  - With it on, `-Xms = -Xmx =` the entered value (the Unity field defaults to 2048).
  - The GC is generational ZGC: no compressed oops (8-byte references), and the committed heap stays
    resident.
- **Load stutter:** the first 10–30 s after joining a world.
- **CPU per frame:** Minecraft is draw-call bound. LTW turns each of Sodium's multi-draws into single
  draws, and the world renders once per eye. The user plays at minimum settings.

## Versions (QuestCraft 6.0.0 from SideQuest, treated as the latest release)

- **Minecraft and mods:** Minecraft 1.21.5 on Fabric. The mod list comes from Pojlib's `mods.json` on
  branch `QuestCraft-6.0.0`, fetched live at every launch:
  - Vivecraft **1.2.5** (release `1.21.5-1.2.5-OpenXR`) in the official app. **Our build ships Vivecraft
    1.3.15** (see `vivecraft/` below) and installs it over the downloaded one.
  - Not in `mods.json`: **our build adds ImmersiveMC 1.6.0-beta1** (`immersivemc/`, key `immersivemc.install`).
  - Sodium **0.6.13**, Lithium 0.16.3, FerriteCore 8.0.0, ImmediatelyFast 1.9.7, MoreCulling 1.3.1,
    EntityCulling 1.9.5, BadOptimizations 2.4.1, Krypton 0.2.9, Simple Voice Chat 2.6.11, bbrb 1.5.2,
    Fabric API 0.128.2, Cloth Config
  - **No ModernFix for 1.21.5.**
- **Java runtime:** OpenJDK **22** (`jre22-6.0.0`), started inside the app process via `JLI_Launch`.
- **QuestCraft APK:** `QCXR-6.0.0.apk` from the GitHub release, versionCode 40, sha256 `4d474dff…b783c`.
  `tools/apkinfo.sh` checks that the headset runs this exact APK (SideQuest's copy may differ).
- **LTW:** a build of `artdeell/LTW` commit `3122623` (2025-04-07).

## Hardware and runtime facts

- **Quest 3S:** Snapdragon XR2 Gen 2, 8 GB shared RAM, default eye buffer ~1680×1760, refresh
  72/90/120 Hz.
- **Stack:** Unity launcher (QCXR-XR-Wrapper) → Pojlib → OpenJDK on Android → Fabric → Minecraft + this
  mod + the mods above → LTW → OpenXR with the **OpenGL ES** graphics binding.
- **Package / data dir:** `com.qcxr.qcxr`, `/sdcard/Android/data/com.qcxr.qcxr/files/`, which holds
  `latestlog.txt`, `custom_env.txt`, `claudecraft.properties` and the instances. Our side-by-side build
  uses `com.qcxr.qcxr.claudecraft`. **Verify the exact instance and mods paths on device before relying
  on them.**

## Repo layout

| Path | What |
|---|---|
| `vivecraft/` | Vivecraft 1.21.5-1.3.15 with QuestCraft's OpenXR provider: QuestCraftPlusPlus/VivecraftMod `OpenXR-1.21.5` @ `612a11ca` (upstream 1.3.4 + OpenXR, unreleased) merged with upstream Vivecraft/VivecraftMod `1.21.5-1.3.15` @ `86da32bd`. The merge is local commit `463d0f64` (its message lists the conflict resolution), squash-merged as a git subtree; our changes are normal commits. The 1.2.5 line (base `6d9b96af`, the `1.21.5-1.2.5-OpenXR` release) is kept on branches `archive/vivecraft-1.2.5` (integration) and `archive/vivecraft-1.2.5-main`. CI still compares the jar with the 1.2.5 release jar, for information only. |
| `immersivemc/` | ImmersiveMC (hammy275/immersive-mc) @ `8713ae83`, branch `1.21.5`, vendored without the demo GIFs. CI builds it for Fabric against our Vivecraft jar (`-Pvivecraft_jar`); the APK bundles it and the launcher installs it as `mods/ImmersiveMC-claudecraft.jar` (`immersivemc.install`). Our changes are listed in `immersivemc/IMPORTED.md`. |
| `pojlib/` | QuestCraftPlusPlus/Pojlib @ `c6566d5` (`QuestCraft-6.0.0`), vendored without binaries. The 6.0.0 APK was built from an older commit (≈`092c27e`, 2025-05-30); the one later change to a class we replace is reverted (see `pojlib/IMPORTED.md`). Not built as a whole. |
| `apk/` | APK pipeline: `build-apk.sh`, `patched-sources.txt` (the launcher classes we change), `rename_package.py`, `compare_jars.py`, and the debug signing key |
| `.github/workflows/build.yml` | CI. Builds the jar and the APKs; a `mihail.N` tag publishes a GitHub release |
| `tools/` | Device scripts (adb), run on the user's Mac |
| `perf/` | `FINDINGS.md` (audit), `RESULTS.md` (measurements), `runs/` (raw data, git-ignored) |

## Code map: Vivecraft (under `vivecraft/common/src/main/java/org/vivecraft/`)

| Area | File |
|---|---|
| Per-frame pass loop; each eye calls the full `gameRenderer.render` | `client_vr/render/helpers/VRPassHelper.java` |
| Frame loop order: `vr.poll` / `xrWaitFrame` at "render" after the game ticks (QuestCraft `c4f3315`, in the base), or before them like upstream with `xrLateFrameWait` off | `mixin/client_vr/MinecraftVRMixin.java` |
| Which passes run; all framebuffer allocation | `client_vr/provider/VRRenderer.java` (`getRenderPasses`, `setupRenderConfiguration`) |
| Entity-outline target, one per render pass | `client_vr/MultiPassTextureTarget.java`, `mixin/client_vr/renderer/LevelRendererVRMixin.java` |
| OpenXR session, input, refresh rate (`initDisplayRefreshRate` picks the **max**) | `client_vr/provider/openxr/MCOpenXR.java` |
| Swapchain images, frame submit | `client_vr/provider/openxr/OpenXRStereoRenderer.java` |
| Android glue; fixed 2.5 s Unity hand-off sleep | `client_vr/provider/openxr/DeviceCompat.java`, `util/VLoader.java` |
| VR start after the first resource load | `mixin/client/ResourceLoadStateTrackerMixin.java`, `client_vr/VRState.java` |
| Forces a Sodium chunk-graph rebuild **every pass** (the eyes differ anyway; see FINDINGS V11) | `mod_compat_vr/sodium/mixin/SodiumWorldRendererVRMixin.java` |
| Second-eye dedup today (lighting only) via `isFirstPass` | `mixin/client_vr/renderer/LevelRendererVRMixin.java` |
| Title-screen 3D world: never freed on join upstream; decoded into one buffer, in parallel (1.3.x) | `client_vr/menuworlds/MenuWorldRenderer.java`, `MenuWorldExporter.java` |
| Supporter list download; keeps `Player` objects if it fails | `client/network/SupporterReceiver.java` |
| All user settings and the settings GUI | `client_vr/settings/VRSettings.java`, `client/gui/settings/` (screens in `client/gui/framework/`) |
| Eye targets: `getLeftEyeTarget()`/`getRightEyeTarget()`/`getEyeTarget(i)`; OpenXR returns the acquired swapchain image, the others `framebufferEye[i]` | `client_vr/provider/VRRenderer.java` |
| Desktop provider with no headset (CPU profiling on the Mac) | `client_vr/provider/nullvr/` |

## Code map: launcher (under `pojlib/src/main/java/pojlib/`)

| Area | File |
|---|---|
| JVM flags, heap sizing, `custom_env.txt`, logcat capture | `util/JREUtils.java` |
| Launch path, per-launch mod updates | `InstanceHandler.java`, `API.java`, `util/json/MinecraftInstances.java` |
| Default configs written on instance creation | `install/Installer.java#moveLocalAssets`, `src/main/assets/` |
| Unity host activity (pause/resume) | `UnityPlayerActivity.java` |
| Launcher log (deleted at every start upstream) | `util/Logger.java` |
| Our settings (`claudecraft.properties`); bundled jars installed at each launch | `util/ClaudecraftConfig.java`; `util/BundledVivecraft.java`, `util/BundledImmersiveMC.java` |

The Unity C# (QCXR-XR-Wrapper) is compiled to native code inside the APK, so we cannot change it.

## Rules

1. **Every change goes behind its own toggle.**
   - Vivecraft changes: a `VRSettings` option, visible in the VR settings GUI ("Performance and
     stability" page).
   - Launcher changes: a key in `claudecraft.properties` in the app's files dir.
   - Every feature defaults to **on** (user decision, 2026-10-15); the switch turns it off for A/B tests.
     Defaults are listed in `PLAN.md`.
   - One build must allow A/B testing in a single headset session.
2. **Log the config.** On VR init, log one line prefixed `[VCPERF]` with the build tag and every perf
   toggle's value. The launcher logs its effective JVM arguments and properties into `latestlog.txt`. Any
   log must identify what was tested.
3. **Build tag:** set `mod_version` in `vivecraft/gradle.properties` to `1.3.15-mihail.N` and bump N for
   every build deployed to the headset. `1.3.15-mihail.0` is the merged base without our changes (the
   1.2.5 builds were `1.2.5-mihail.N`). Tag the commit `mihail.N` to publish the jar and APKs as a GitHub
   release (this session's git proxy refuses tag pushes; push the tag from the Mac).
4. **Minimal, rebase-friendly diffs.** Prefer Mixins and small hooks over rewrites. Do not reformat files.
   Keep upstream rebases cheap.
5. **No new dependencies.**
6. **Never claim a win without data.** Every perf change gets a before/after entry in `perf/RESULTS.md`
   from the measurement protocol below. "Builds" and "runs" are not results.
7. **The human wears the headset.** Before each test session, write a short numbered test script for the
   user (what to do, for how long). After it, pull data and analyse it yourself.
8. **Small branches:** one branch per task (`perf/<task>`), merged into `main` once measured.
   Headset test builds come from an `integration/<tag>` branch that merges the task branches.
9. **Privacy:** `logcat` contains the Minecraft access token (Pojlib prints the launch arguments). Never
   commit raw logs, and redact `--accessToken` in anything that leaves the machine.

## Build

- **This cloud container cannot reach** Fabric, Mojang, Modrinth, jitpack or the Android SDK hosts. All
  builds run on **GitHub Actions** (`.github/workflows/build.yml`) on every push to `main`, `perf/**` or
  `integration/**`. Watch runs with the GitHub tools.
- **Jar:** with network, `cd vivecraft && ./gradlew :fabric:build` (JDK **21**) →
  `vivecraft/fabric/build/libs/vivecraft-1.21.5-<mod_version>-fabric.jar`.
- **APK:** `apk/build-apk.sh` patches the released QuestCraft APK:
  - It swaps in the launcher classes listed in `apk/patched-sources.txt`, compiled against the APK's own
    classes.
  - It bundles our jar as `assets/claudecraft/Vivecraft.jar`.
  - It signs with `apk/claudecraft-debug.keystore` (password `claudecraft`). That key is for personal
    sideloading only. Keep using it so updates install over each other.
- **APK variants:**
  - `replace` keeps package `com.qcxr.qcxr`. Installing it requires uninstalling the official app first,
    which **deletes its data folder**; back it up.
  - `side` uses package `com.qcxr.qcxr.claudecraft` and installs next to the official app with its own
    data folder.
- **Publishing:** a tag `mihail.N` publishes both APKs and the jar as a GitHub release.

## Device loop (scripts live in `tools/`, run on the user's Mac with adb)

- `tools/forensics.sh` — before anything else after a crash, save Android's exit records (`dumpsys
  activity exit-info`), dropbox crash entries, `latestlog.txt`, `latest.log`, `crash-reports/` and
  `hs_err_pid*.log` into `perf/runs/<timestamp>/`, with the access token redacted.
- `tools/deploy.sh` — quick iteration without a new APK. Back up the on-device jar once
  (`Vivecraft.jar.orig`), push a new jar **under the same filename**, print the pushed `mod_version`.
- `tools/restore.sh` — put the original jar back.
- `tools/apkinfo.sh` — the installed APK's version and sha256 against the GitHub 6.0.0 release, and the heap
  size the launcher picked last.
- `tools/logs.sh` — pull `latest.log`, `latestlog.txt`, the GC log and crash reports into
  `perf/runs/<timestamp>/` (redacted).
- `tools/mem.sh <label>` — `adb shell dumpsys meminfo <pkg>` plus per-thread CPU (`top -H`) into the
  current run folder, tagged with the label.
- `tools/metrics.sh` — pull OVR Metrics Tool CSV captures (verify the capture path on device).

The launcher re-downloads a mod only when its manifest version changes, so a pushed jar survives
relaunches. Our APK also reinstalls its bundled jar at every launch.

## Measurement protocol

- **World and settings:** a fixed test world (pregenerated, fixed seed), the user's usual settings
  (render distance as played, simulation distance minimum), the same spawn spot.
- **Memory snapshots:** `title` (title screen), `load` (≈10 s into joining), `settled` (60 s after
  joining), `walk` (after 2 min walking), and `long` (after 20–30 min, since in-game crashes come late).
- **Reading meminfo:** the `dumpsys meminfo` **"Java Heap" row is ART's heap, not Minecraft's**. The
  OpenJDK heap shows up in another row. Use the `[VCPERF] mem` log lines (heap, metaspace, code cache,
  direct buffers, RSS breakdown) for the Java side, and the meminfo `TOTAL` and `Graphics` rows for the
  process.
- **GC:** the patched launcher writes a GC log (`gc.log` in the files dir). Count `Allocation Stall`
  lines and note the heap after GC.
- **During play:** OVR Metrics capture (CPU/GPU frame time, FPS, CPU/GPU level, memory).
- **Crashes:** classify every crash with `tools/forensics.sh` (exit reason and memory at death) before
  relaunching.
- **Record in `perf/RESULTS.md`:**
  - build tag, toggles;
  - the meminfo `TOTAL` for each snapshot, plus the `Native Heap` and `Graphics` rows;
  - the `[VCPERF] mem` heap and RSS figures;
  - median and p95 frame time;
  - crashes (yes/no, with exit reason).
