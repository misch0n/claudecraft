# perf/FINDINGS.md: optimisation hunt, QuestCraft + its Vivecraft fork

Date: 2026-10-13. **Nothing here has been measured yet.** These findings come from reading code
only. Magnitudes are estimates. Every item still needs a before/after entry in `perf/RESULTS.md`
before it counts as a win (CLAUDE.md rule 6).

## What was read

| Component | Repo @ commit | Role |
|---|---|---|
| Vivecraft fork | `QuestCraftPlusPlus/VivecraftMod` `OpenXR-1.21.5` head `612a11c` (2025-11-28); tag `1.21.5-1.2.5-OpenXR` = `97b4cf8`; fix commits `bdaefe97`..`6d9b96af` | the VR mod (our fork target) |
| Pojlib | `QuestCraftPlusPlus/Pojlib` `QuestCraft-6.0.0` `c6566d5` (2026-09-02) | launcher core: JVM start, heap, mod manifest, shipped configs |
| Unity launcher | `QuestCraftPlusPlus/QCXR-XR-Wrapper` `master` `5aeb178` (scripts + settings only) | UI process host |
| Sodium | `CaffeineMC/sodium` 0.6.13 for 1.21.5 | to check what the Vivecraft mixins change |
| Upstream Vivecraft | `Vivecraft/VivecraftMod` `openxr` (now MC 26.2) | cherry-pick source |
| LTW | `artdeell/LTW` `3122623` (2025-04-07): matches the shipped `libltw.so` build date | GL to GL ES translation layer (shipped as a prebuilt `libltw.so`) |

Legend: **✔** = checked in source by the lead session. **◐** = reported by a sub-review and
plausible, but not re-checked line by line. **est** = estimate, unmeasured.

---

## 1. Facts that change the plan

1. **The shipped Vivecraft is neither the release tag nor the branch head.** ✔
   - The manifest (`Pojlib/mods.json`, branch `QuestCraft-6.0.0`) pins
     `vivecraft-1.21.5-1.2.5-fabric.jar` from release `1.21.5-1.2.5-OpenXR` (tag `97b4cf8`, 2025-06-09).
   - At that tag, `OpenXRStereoRenderer.createRenderTexture` cannot work. It recreates both
     framebuffer arrays per eye and indexes with `i` instead of `i1`, so the left eye targets are null.
   - Three same-day commits fix this (`bdaefe97`, `532f26e3`, `6d9b96af`), so the jar was almost
     certainly built from one of them. Most likely `6d9b96af`, with `mod_version=1.2.5`.
   - **Confirm by comparing the on-device jar's classes against local builds.**
2. **Branch head is not a safe base as-is.** ✔ Head is 245 commits after `6d9b96af` (`mod_version=1.3.4`). It has
   the frame-timing fix (`c4f3315`), but merge `885577dd` dropped the "precreated texture" path:
   - Swapchain images are now created through `vivecraft$createFixedIdTexture`, which calls
     `glTexImage2D` on the runtime-owned swapchain textures.
   - On Quest this either fails VR init (immutable storage) or renders into private storage that the
     compositor never sees.
   - Head also allocates two unused eye-size textures and starts a bHaptics client (section 4, V10).
3. **The stack** ✔ is MC 1.21.5, Fabric API 0.128.2, Sodium **0.6.13**, Lithium 0.16.3, FerriteCore 8.0.0,
   ImmediatelyFast 1.9.7, MoreCulling 1.3.1, EntityCulling 1.9.5, BadOptimizations 2.4.1, Krypton 0.2.9,
   Simple Voice Chat 2.6.11 and bbrb 1.5.2.
   - **There is no ModernFix for 1.21.5** (1.21.4 and older include it), although its config is still shipped.
   - The JRE is **JDK 22** (`android-openjdk-build-multiarch` tag `jre22-6.0.0`).
4. **JVM flags** ✔ (`Pojlib JREUtils.java:203-240`):
   - Heap: custom RAM gives `-Xms=-Xmx=memoryValue` (API default 1800 MB). Auto mode gives
     `-Xms1024M -Xmx=max(0.8 × (availMem − threshold at launch), 1536)`.
   - GC: `-XX:+UseZGC -XX:+ZGenerational -XX:-ZProactive -XX:+DisableExplicitGC`.
   - Other: `-XX:-ImplicitNullChecks`, `-XX:+UseCriticalJavaThreadPriority`, `-XX:+UseSignalChaining`.
5. **Zero-rebuild levers exist.** ✔
   - Mod jars are stored by slug (`mods/Vivecraft.jar`). They are re-downloaded only if the manifest version
     changes, and jars Pojlib doesn't know about are left alone. So pushed and hand-added jars survive relaunches.
   - `custom_env.txt` in the app's files dir (`/sdcard/Android/data/com.qcxr.qcxr/files/`, to verify on
     device) is turned into environment variables before the JVM starts. The JVM is started via
     `JLI_Launch`, so **`JAVA_TOOL_OPTIONS` works**. Command-line flags still win for the same option, and
     adding a second GC makes the JVM refuse to start.
6. **Unity stays alive in the same process.** ✔ After the game is ready it only deinitialises its XR loader
   (`InstanceButton.cs:25-31`). The Unity player keeps running and is not paused:
   - Settings: `runInBackground: 1`, Android quality "Medium" (4× MSAA, shadows), Vulkan.
   - `InstanceButton.Update()` makes a JNI call every frame.
   - `Microphone.Start(..., loop=true, ...)` (`JNIStorage.cs:47`) is never stopped.
   - **Its CPU and memory cost while Minecraft runs is unknown. Measure it (M3).**
7. **QuestCraft's shipped Vivecraft defaults** ✔ include `menuWorldSelection=BOTH` and `displayMirrorMode=OFF`.
   `CheckVivecraftConfig` re-forces some keys on every launch, such as `vrHotswitchingEnabled=false` and
   `alwaysShowUpdates=false`.

---

## 2. Measurement protocol fixes (do these before the Phase 0 baseline)

- **M1. The "Java Heap" row in `dumpsys meminfo` is ART's heap, not Minecraft's.** The OpenJDK ZGC heap is a
  memfd mapping and will appear in another row (probably `Other mmap` or `Unknown`; verify). ZGC commits with
  `fallocate` on that memfd, so committed means resident (inference from HotSpot's Linux ZGC backing; verify).
  With `-Xms = -Xmx` the full heap is resident from launch and never uncommitted.
- **M2. GC log, no rebuild needed.** Add one line to `custom_env.txt`:
  `JAVA_TOOL_OPTIONS=-Xlog:gc*:file=/sdcard/Android/data/com.qcxr.qcxr/files/gc.log:uptime,level,tags`
  Then read pause times, **`Allocation Stall`** lines (a suspected cause of join stutter), and heap after GC
  (the real live set, which tells us how big the heap must be).
- **M3. Per-thread CPU.** Use `adb shell top -H -b -n 1 -p $(adb shell pidof com.qcxr.qcxr)` during play. It
  shows the Unity threads, the render thread, the server thread, ZGC workers, chunk builders, Voice Chat and
  EntityCulling.
- **M4. GPU memory.** Try `adb shell dumpsys gpu --gpumem` (if Quest OS has it), alongside the meminfo
  `Graphics` / `GL mtrack` rows.
- **M5. In-process memory probe** (the first code task: logging only, behind a toggle). This splits memory
  reliably on a non-rooted headset. Log `[VCPERF] mem` lines at title, join start, join done, and every 30 s
  with:
  - MXBeans: heap committed/used, metaspace, code cache, direct buffers, and GC counts/times (ZGC cycles
    and pauses).
  - `/proc/self/status` (`VmRSS`, `RssAnon`, `RssFile`, `RssShmem`).
  - `/proc/self/smaps_rollup`.
  - A by-name smaps rollup at milestones only (it is expensive).
- **M6. Identify the shipped jar.** Pull `mods/Vivecraft.jar` and compare its classes with local builds of
  `6d9b96af` and its neighbours. Record the result in `RESULTS.md`.
- **M7.** Read the launcher log line `QuestCraft: Setting JVM memory to N MB` to learn which heap mode is in use.
  The user has QuestCraft **6.0.0** (SideQuest). Its mod manifest is fetched live from Pojlib branch
  `QuestCraft-6.0.0`, but the JVM flags and Unity code are compiled into the APK and may predate the branch
  head read here. To confirm the real flags, have the M5 probe log
  `ManagementFactory.getRuntimeMXBean().getInputArguments()` (JVM options only, no game arguments).
- **M9. Crash forensics: classify every crash before changing anything.**
  - **Evidence is lost on relaunch.** ✔ The launcher deletes `files/latestlog.txt` when QuestCraft starts
    (`Logger.java:29`) and runs `logcat -c` when Minecraft starts (`JREUtils.java:112`). After a crash, pull logs
    **before** relaunching.
  - **Records that survive relaunches** (no root needed; verify on Quest OS):
    - `adb shell dumpsys activity exit-info com.qcxr.qcxr`: Android's record of recent process deaths, with
      reason (low memory, native crash, signal, …), timestamp and PSS/RSS at death.
    - `adb shell dumpsys dropbox --print data_app_native_crash` (also `data_app_crash`, `SYSTEM_TOMBSTONE`):
      native crash backtraces, for example in LTW, the driver or the JVM.
    - Instance folder: `crash-reports/` (Minecraft) and `hs_err_pid*.log` (JVM fatal errors; written to the
      game directory if the JVM gets the chance).
  - **User report (2026-10-13):** launch crashes are more likely when relaunching right after a crash; the
    2nd or 3rd try works; waiting helps. A broken file would not heal by waiting, so this points at transient
    state. Candidates:
    - (a) The OS is still reclaiming memory after a memory kill. Background services restart, and the
      compositor releases the dead session's buffers asynchronously. That leaves less headroom at the VR-start
      memory peak, and in auto mode the heap limit is computed from free RAM at that moment.
    - (b) The OpenXR runtime is still tearing down the dead session, combined with V12b's fixed 2.5 s hand-off.
    - (c) Thermal throttling after a long session slows startup and eats the hand-off margin.
  - The exit-info reason and memory-at-death distinguish the launch crash from the in-game crash.
- **M8. Privacy: `logcat` contains the Minecraft access token.** ✔ `Pojlib JREUtils.java:243` runs
  `System.out.println(JVMArgs)`, and those arguments include `--accessToken`. `tools/logs.sh` must not save raw
  `logcat` into anything that leaves the machine, or must redact `--accessToken <…>` first. Have the script grep
  `latest.log` for the token too before sharing. It shouldn't be there, but this hasn't been verified.
  `perf/runs/` is git-ignored regardless.

---

## 3. Experiments that need no code (run them with the baseline)

| # | Change (where) | Expectation | Measure |
|---|---|---|---|
| Z1 | Menu world **off** (VR Settings, menu world NONE) | −170 to −400 MB heap (est) plus GPU buffers (V1) | M5/M1, join stutter (M2) |
| Z2 | Heap 1800 vs 1400 MB (QuestCraft custom RAM) | −400 MB resident if the live set allows | M2 live set, stalls, crashes |
| Z3 | GC log on (M2), with no other change | decides the launcher GC recommendation (Q1) | stall count and duration during join |
| Z4 | One optional mod removed at a time (Simple Voice Chat first, if not needed) | thread and memory share | M3, M5 |
| Z5 | Add ModernFix for 1.21.5 by hand, only if a compatible release exists | model/resource memory | M5; watch for incompatibility |
| Z6 | `LIBGL_NOERROR=1` in `custom_env.txt` (LTW L4) | shows whether `glGetError` costs anything (V9) | CPU frame time; test only, it hides GPU OOM |

---

## 4. Vivecraft fork: code findings, ranked by expected value

"In" says which build has the issue. **S** = shipped (`6d9b96af`, assumed); **H** = branch head.

### Memory and join stutter

**V1. The menu world stays in memory for the whole session, and its load spikes the heap.** ✔ In: S, H.
- **Where:**
  - `MenuWorldRenderer.java:604` `destroy()` frees the GPU buffers but keeps `blockAccess`. Nothing tears the
    menu world down on world join.
  - `MenuWorldExporter.java:178-185` inflates the whole file into a pre-sized `ByteArrayOutputStream`, then
    `toByteArray()` copies it again.
  - It then builds `BlockState[x*y*z]` (`:290`), which costs 8 B per block because ZGC has no compressed oops,
    plus two full-size `byte[]` light maps (`:308-309`).
  - ◐ `MinecraftVRMixin:224-232` keeps building while VR is up, even in a world. ◐ `ReloadListener:39-50`
    rebuilds it on any resource-pack change, including server packs mid-game.
- **Estimate:** resident heap ≈170 MB for a 256³ world, ≈400 MB for 320×384×320 (est). Transient peak during
  load is 2× the uncompressed size on top of that. GPU buffers are unknown.
- **Fix (PLAN 1b, extended):**
  - Unload on join (`cancelBuilding`, `destroy`, drop `blockAccess`) and rebuild on the title screen.
  - Don't load or build while a level exists; mark it dirty on reload instead.
  - Inflate as a stream instead of two full copies.
  - Later: palette `short[]` blocks and packed light (~3× smaller).
  - Toggle `freeMenuWorldInGame` (default on, per PLAN).

**V2. The entity-outline target is duplicated for every render pass.** ✔ In: S, H.
- **Where:** `LevelRendererVRMixin.java:317-326` makes a `MultiPassTextureTarget`, which creates an eye-size
  target with depth for every `RenderPass` (`MultiPassTextureTarget.java:45-50`). LEFT and RIGHT each get their own.
- **Estimate:** ≈75 MiB GPU, against ≈7 MiB in vanilla. It is only used for glowing entities.
- **Fix:** share the target between the eyes (−22 MiB), and create targets on first use (−~69 MiB).
  Fold into PLAN 1c.

**V3. Auxiliary targets are always allocated.** ◐ Sizes come from the review; the allocation site was ✔ in
`VRRenderer.setupRenderConfiguration`. In: S, H.
- **Targets and sizes:**
  - Camera + camera render: 1920×1080 ×2 with depth, ≈31.6 MiB. The camera target's depth is never used.
  - Telescopes: ≈7.9 MiB.
  - Keyboard: ≈7 MiB, unused while the physical keyboard is on (the default).
  - Mirror: ≈3.5 MiB. Nothing displays it, because the EGL surface is a 0×0 pbuffer (`pojlib egl_bridge.c:138`).
- **Fix:** PLAN 1c as written. ≈50 MiB, plus V2.

**V4. If supporter data fails to download, every player object is kept forever.** ✔ In: S, H.
- **Where:** `SupporterReceiver.java:63-90` queues every `Player` created (the local player on each join,
  respawn or dimension change, and every remote player).
- **Mechanism:** on `IOException` the download thread throws without setting `DOWNLOAD_FAILED`, so the queue
  is never cleared. No timeouts are set either.
- **Effect:** each retained `LocalPlayer` pins its old `ClientLevel` (chunks and entities).
- **When:** offline play, vivecraft.org down, or certificate problems.
- **Fix:** catch `Throwable`, set the failure flag, clear the queue, queue `(UUID, name)` instead of
  `Player`, and add timeouts. Risk: none.

**V5. Smaller retention and lifecycle issues.** ◐
- Trackers keep the last hit entity across disconnect (`EntityInteractionModule`, `SwingTracker`), which pins
  the old world during the next join.
- Head only: pose-history map not cleared on dimension change; `ServerConfig.init` on every resource reload
  (a synchronous TOML load on the render thread); a haptic executor that is never shut down.

### Frame pacing and CPU

**V6. Shipped build: the game tick runs between `xrWaitFrame` and rendering.** ✔ In: S (fixed in H by `c4f3315`).
- **Where:** `vr.poll()` (which calls `xrWaitFrame`) ran at the start of `runTick`. Packet handling, up to
  10 ticks, sound and so on came after it, and only then rendering. `endFrame` came after the extra
  passes and `postRender`.
- **Effect:** every frame that carries a 20 Hz tick loses that tick's time from its deadline. Likely
  periodic missed frames, worse while chunks load.
- **Fix:** backport `c4f3315` behind a toggle. ◐ Head introduced a side effect: the partial tick is sampled
  before the wait and input is applied after ticks. Fix that in the same task.

**V7. The refresh rate is forced to the highest offered (120 Hz on the 3S).** ✔ In: S, H.
- **Where:** `MCOpenXR.initDisplayRefreshRate` (`:891-900`) takes the last enumerated rate.
- **Effect:** an 8.3 ms budget for a CPU-bound game that renders twice, more compositor work, and earlier
  thermal throttling.
- **Fix:** PLAN 1a as written. Default 90.

**V8. OpenXR input queries bound sources for every action, every frame.** ✔ In: S, H.
- **Where:** `MCOpenXR.readBoolean/readFloat/readVecData` call `getOrigins(action)` (`:939-972`) on every
  read. Each call does two `xrEnumerateBoundSourcesForAction` calls plus `Arrays.stream(...).boxed().toList()`.
- **Also:** ◐ `VRInputAction.isEnabled()` re-queries for higher-priority actions.
- **Scale:** roughly 100 actions, so hundreds of runtime calls per frame, all on the critical path after
  `xrWaitFrame`.
- **Estimate:** 0.5–2 ms/frame (est).
- **Fix:** cache origins per action and invalidate on `XR_TYPE_EVENT_DATA_INTERACTION_PROFILE_CHANGED`
  (today that event is received and ignored). Skip inactive action sets. Toggle `cacheInputOrigins`.

**V9. `glGetError` runs about 9 times per frame, and the message strings are built eagerly.** ✔ In: S, H.
- **Where:** `RenderHelper.checkGLError` (`:565`) is unconditional. It is called from `VRPassHelper`
  (per eye) and `MinecraftVRMixin`.
- **Cost:** through LTW and a possibly threaded driver, each call can force a sync. Unknown; could be
  negligible or several ms. Z6 (`LIBGL_NOERROR=1`) measures it before any code is written.
- **Fix:** toggle `glErrorChecks` (default off on Android) and build the strings lazily. Risk: none.

**V10. Head only.** ✔ unless marked. Matters only if we base on head.
- (a) `glTexImage2D` on swapchain images (fact 2).
- (b) `VRRenderer.java:672-690` allocates `framebufferEye0/1` (2 × 11.3 MiB) that OpenXR never uses.
- (c) `Haptics.connect()` runs at every VR init (`VRState.java:93`) with reconnect enabled. There is no
  bHaptics server on Quest; cost ◐.
- (d) ◐ In mirror modes other than OFF, the mirror samples a swapchain image after release.

**V11. Shared culling across both eyes (PLAN Phase 2): the design needs a correction.** ✔
- **What was checked in Sodium 0.6.13:** each eye has a different camera position, so `SodiumWorldRenderer.setupTerrain`
  sees `cameraLocationChanged` on every pass anyway. It marks the graph dirty, rebuilds render lists, and
  processes GFNI translucency movement for the 63 mm eye-to-eye jump.
- **Consequence:** the "always dirty" Vivecraft mixin is a symptom, not the cause, and deleting it alone saves
  nothing.
- **The win needs both:**
  1. One union frustum for both eyes, from the HMD centre moved back ~3 cm, using the max tangent from each eye's `XrFovf`.
  2. On the RIGHT pass of the same frame, skipping Sodium's `markGraphDirty`/`update`/GFNI/`updateChunks`/
     `uploadChunks`/`tickVisibleRenders` (for example with `@WrapWithCondition`).
- **Fallback:** use per-eye culling when the two eyes are in different sections.
- **Optional:** reuse the visible-entity list for the RIGHT eye (`LevelRendererVRMixin:140-150` has no
  first-pass gate) ◐.
- **Estimate:** 0.3–1.5 ms for terrain plus 0.1–0.6 ms for entities (est). Desktop nullvr profiling first.

**V12. The OpenXR session state is mostly ignored.** ✔ `shouldRender` is stored and never read (`MCOpenXR.java:193`).
- ◐ Once the session stops (Home, headset off), `xrWaitFrame` fails immediately. Both eyes then render with
  no pacing and errors are logged every frame.
- ◐ `LOSS_PENDING` is ignored.
- **Effect:** heat and battery drain while the user isn't playing.
- **Fix:** track the session state; when not running, skip passes and sleep; honour `shouldRender`;
  rate-limit `logError`.

**V12b. The Unity → Vivecraft XR hand-off is timed by a fixed sleep (suspect for intermittent launch crashes).** ✔ code; crash link is a hypothesis.
- **Vivecraft side:** VR starts on the first tick after Minecraft's initial resource load
  (`ResourceLoadStateTrackerMixin:27-40`, `MinecraftVRMixin:198`). `DeviceCompat.initOpenXRLoader` sets Unity's
  `gameReady` flag, sleeps a fixed 2.5 s, then creates its own OpenXR loader, instance and session.
- **Unity side:** Unity reacts in `InstanceButton.Update()` with a 1 s fade plus a 200 ms delay before
  `DeinitializeLoader()`. A busy device (post-load GC, render targets being allocated, menu-world decode
  starting) can eat the ~1.3 s margin.
- **The same moment is also the memory peak:** Unity's XR session, Minecraft's post-load heap, ~185 MB of new
  render targets and the menu world's 2× decode.
- **Fix:** replace the sleep with an explicit handshake (wait until Unity reports XR deinitialised, with a timeout).
- **Measure:** logcat timestamps around VR init. Check whether launch crashes show the memory-limit message
  (points to the memory peak) or not (points to the race).

**V13. No thread or performance hints are sent to the runtime.** ✔ The extension list (`MCOpenXR.java:636-662`)
lacks `XR_KHR_android_thread_settings` and `XR_EXT_performance_settings`.
- **Cost of leaving it:** unknown. Meta's scheduler may not treat the JVM render thread as the app's main
  render thread.
- **Plan:** an A/B toggle is cheap. Note that `callocPointer(5)` is already full and must grow first.

**V14. Smaller CPU items.** ◐ Each is ≤0.3 ms (est).
- Weather columns are computed for both eyes.
- A redundant per-eye clear before `gameRenderer.render`.
- The HUD is redrawn every frame.
- About 20–50 KB of garbage per frame (`VRDevicePose` getters, streams in `setupOverlayStatus`).
- Startup only: a fixed `Thread.sleep(2500)` in `DeviceCompat.initOpenXRLoader` ✔, and a busy-wait with no
  timeout for session READY ◐.

**V15. GPU on a tile-based renderer.** ◐ Low priority while CPU-bound.
- Each eye does a full-screen copy from "3D Render" into the swapchain.
- `glInvalidateFramebuffer` is never called, so depth is stored and the swapchain is loaded every frame.
- **Cheap fix:** invalidate. **Larger fix:** render straight into the swapchain (already PLAN Phase 3).
- `XR_KHR_visibility_mask` is unused; masking the hidden area would cut fragment work.

---

## 5. QuestCraft launcher (Pojlib + Unity): recommendations

These live in separate repos (Pojlib, QCXR-XR-Wrapper). Changing them means building the launcher and the
Unity APK, so for us they are recommendations backed by data (PLAN Phase 3), plus the zero-rebuild levers above.

- **Q1. ZGC on a 1.4–1.8 GB heap is a questionable choice for an 8 GB shared-memory device.** ✔ flags; impact is inference.
  - (a) There are no compressed oops, so every reference costs 8 B instead of 4 B. Minecraft's reference-heavy
    heap is larger than under G1.
  - (b) With `-Xms = -Xmx` the whole heap is committed (and resident) from launch.
  - (c) ZGC needs heap headroom and concurrent GC threads on 6 mobile cores. During join-time allocation bursts
    that can mean allocation stalls (hypothesis: M2 will show it or refute it).
  - **Candidate:** G1 (compressed oops, uncommit) with a small `-Xms` and `-Xmx` sized from the measured live set.
    This needs a Pojlib change; `JAVA_TOOL_OPTIONS` cannot switch the GC.
- **Q2. Heap size depends on launch-time conditions.** ✔ In auto mode `-Xmx` depends on free RAM at launch, so
  runs aren't comparable and the heap can grow past what the device can afford. Measure with a fixed custom value.
  - **The "1.8 GB heap" in CLAUDE.md is probably not what runs.** ✔ The Unity launcher's custom-RAM setting is
    off by default, and its default value is 2048 (`ConfigHandler.cs:41-42`). Pojlib's `1800` only applies when
    custom RAM is on but no value was passed.
  - With custom RAM off (the default), `-Xmx = max(0.8 × (free RAM at launch − threshold), 1536 MB)`, so plausibly
    2.5–3 GB (est). ZGC grows the heap toward that maximum and only uncommits above `-Xms` after 5 minutes idle.
  - The launcher log `files/latestlog.txt` line `QuestCraft: Setting JVM memory to N MB` (plus "(Custom)") says
    which applies.
- **Q3. `-XX:-ImplicitNullChecks` adds explicit null checks to all JIT code.** ✔ That is a CPU cost of unknown
  size. It is probably a workaround for Android signal handling. Investigate only.
- **Q4. Unity keeps running beside the game** (fact 6). If M3 shows Unity threads busy:
  - stop the microphone loop;
  - after `gameReady`, disable cameras or set `Application.targetFrameRate` very low, or pause the player;
  - drop the Android quality level.
  - Also check how much memory the Vulkan device and scene hold (meminfo `.so`/`Graphics` rows before and after launch).
- **Q5.** The manifest pins a June 2025 Vivecraft build. The ModernFix config is shipped for a version that
  doesn't include ModernFix.
- **Q6.** Shipped `options.txt`: `mipmapLevels:4` (+33% atlas memory) and `simulationDistance:4` (clamped to
  the minimum). Sodium options are reasonable (auto builder threads, `use_advanced_staging_buffers`).

---

## 6. LTW (GL to GL ES layer)

**Shipped build:** ✔ Pojlib's current `libltw.so` (commit `325a4b2`, "rollback LTW") embeds
`Build date: Apr 7 2025 18:07:00`. That matches public LTW commit `3122623` (2025-04-07); its log strings
match too. File:line references below are to that commit. Current LTW master has changed several of these
points, but none of those changes ship.

How it dispatches: GL names LTW doesn't override resolve straight to the Adreno driver, at zero extra cost.

- **L1. Sodium's multi-draw becomes one driver draw per entry.** ✔
  - **Where:** `basevertex.c:91-97`: on ES 3.2, `glMultiDrawElementsBaseVertex` is a C loop of
    `glDrawElementsBaseVertex`.
  - **Effect:** Sodium issues one entry per visible face direction per section, so Adreno pays its per-draw
    CPU cost thousands of times per eye, twice per frame.
  - **Consequence:** draw count per eye is the dominant per-eye cost.
    - Shared culling (V11) removes the duplicated *visibility* work but not the second eye's draws; only
      multiview (R1) removes those.
    - Keep Sodium block-face culling on and render distance modest.
- **L2. Shader translation is synchronous and uncached.** ◐
  - **Where:** `shader_wrapper.c:155-181`. Every `glShaderSource` runs a full Mesa GLSL compile to ES 3.20
    on the render thread, rebuilding Mesa's builtin tables each time. The driver then compiles the result again.
  - **Effect:** CPU time and short-lived native allocations on every pipeline compile. A plausible
    contributor to join and resource-load hitches.
  - **Bugs:** a translation failure passes NULL to the driver (likely crash; fixed only in 2026-05), and
    multi-string sources overwrite each other.
  - **Vivecraft side:** compile once at load, never per frame or on resize. Use single-string sources.
- **L3. `glTexParameteri` silently turns `GL_LINEAR` into `GL_NEAREST`** for min and mag filters. ✔ (`main.c:205-210`)
  - **Scope:** removed in LTW in July 2025, but present in the shipped build. In 1.21.5 every linear filter
    set through `GlStateManager._texParameter` becomes nearest.
  - **Effect:** a *quality* problem, not performance. The GUI/HUD texture and other resampled quads in VR
    alias and shimmer.
  - **Fix (cheap, toggle):** set the filters Vivecraft needs with `glTexParameterf` / `glSamplerParameteri`,
    which LTW doesn't remap.
- **L4. `glGetError` goes to the driver unless `LIBGL_NOERROR` is set.** ✔ (`main.c:438-452`)
  - Pojlib doesn't set it. `LIBGL_NOERROR=1` in `custom_env.txt` makes every `glGetError` return 0, which gives
    an A/B test for V9 with no rebuild (Z6).
  - Caveat: Minecraft uses `glGetError` to detect GPU out-of-memory on texture allocation. Test only.
- **L5. Calls that stall or misbehave.** ◐ None are used per frame by Vivecraft today, as far as was checked.
  Keep it that way.
  - `glGetTexImage` is a synchronous `glReadPixels` through a temporary framebuffer.
  - `glReadPixels(GL_DEPTH_COMPONENT)` returns nothing, and reallocates and blits on each call.
  - `glMapBuffer` maps the whole buffer with no unsynchronised or invalidate flags.
  - Each `glDrawBuffers` re-attaches every colour target.
  - Desktop-only names (`glDrawBuffer`, `glClearTexImage`, `glMultiDrawElementsIndirect`, …) can be silent no-ops.
- **L6. Clean pass-through:** ◐ `glBlitFramebuffer`, `glInvalidateFramebuffer`, `glMapBufferRange`, fences,
  `glBufferStorage` (native `EXT_buffer_storage`, so Sodium's persistent staging buffer is native),
  instanced and base-vertex draws, and `glFramebufferTextureMultiviewOVR`.
- **Native memory overhead:** small. No shadow copies of buffers or textures; mainly the transient
  allocations during shader translation.

---

## 7. Research items (high ceiling, high effort)

- **R1. Multiview (single-pass stereo).**
  - **History:** Pojlib commit `8293dcd` "OVRMultiview support" (2025-03-08) shipped a *private* LTW build
    ("All rights reserved", with multiview shader strings) and added an `enableOVRMultiview` Vivecraft setting.
    The setting was removed the next day. The public April build that ships now drops multiview shader
    handling: its parser rejects `GL_OVR_multiview2` and `gl_ViewID_OVR` (◐).
  - **Status today:** no public Vivecraft branch contains multiview code. An app would have to bypass LTW for
    multiview shaders, using the driver's own `glCreateShader` with native ES 3.20 source.
  - **Why it matters:** it is the only change that removes the per-eye draw submission, which is the dominant
    per-eye CPU cost.
  - **Cost:** every vanilla and Sodium shader needs `gl_ViewID_OVR`, render targets become texture arrays,
    and LTW must pass the extension through.
  - **Next step:** ask the QuestCraft developers why it was dropped.
- **R2. Upstream cherry-picks.** Upstream `openxr` `62535ff0` "Menuworld optimization (#450)" (faster menu world
  loading via ByteBuffer and threads) is relevant to V1. `cc130fb9` targets the 26.x render API and probably
  doesn't apply.
- The PLAN Phase 3 items (render directly to the swapchain, depth submission / space warp, JFR allocation
  profiling, launcher JVM flags) all still stand.

---

## 8. Proposed changes to PLAN.md / CLAUDE.md (need the user's OK)

1. **Phase 0:**
   - Add M1–M7. Run Z1–Z3 together with the baseline.
   - Base `main` on the **shipped commit** (most likely `6d9b96af`), not branch head, so `mihail.0` reproduces
     what is on the headset.
2. **CLAUDE.md corrections:**
   - Build tag `1.2.5-mihail.N`; expected jar `vivecraft-1.21.5-1.2.5-fabric.jar`.
   - No ModernFix in the 1.21.5 mod list.
   - JDK 22 runtime; Sodium 0.6.13.
   - The meminfo "Java Heap" row means ART, not Minecraft (M1).
3. **Phase 1, reordered by expected value:**
   1. 1b menu world (V1, extended)
   2. new: frame-timing backport (V6)
   3. 1a refresh rate (V7)
   4. new: input origin cache (V8)
   5. new: `glGetError` gate (V9)
   6. new: supporter leak (V4)
   7. new: session-state handling (V12)
   8. 1c lazy buffers, extended with outline sharing (V2, V3)
   9. optional: XR thread/perf hints (V13)
   10. quality, not perf: linear filtering for the GUI and other VR quads despite LTW's remap (L3)
4. **Phase 2:** replace the design with V11 (union frustum plus skipping Sodium's update on the RIGHT eye).
5. **Phase 3:** add R1 (multiview) as a question for the QuestCraft developers, and Q1–Q4 for the launcher write-up.
