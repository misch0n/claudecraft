# PLAN.md — work plan

Phases run in order. Each task gets its own `perf/<task>` branch, toggle and `perf/RESULTS.md` entry.
Headset builds come from `integration/<tag>` branches that merge the task branches, so one build can
A/B every toggle. Stop and report to the user at every **⏸ checkpoint**. The evidence for each task is
in `perf/FINDINGS.md` (IDs like V1, Q1 and M5 refer to it).

---

## Phase 0 — Setup, pipeline, baseline

1. ✅ Repo layout. `vivecraft/` is imported from release commit `6d9b96af` and `pojlib/` from
   `QuestCraft-6.0.0` (see CLAUDE.md).
2. ✅ CI on GitHub Actions: Vivecraft jar → patched QuestCraft APK (replace and side variants). The
   container cannot reach the build hosts.
3. **Confirm the base commit.** CI compares our `mihail.0` jar with the release jar class by class. If
   many classes differ, build the neighbouring commits (`97b4cf8`, `bdaefe97`, `532f26e3`) and pick the
   closest one.
4. **Device scripts** in `tools/` (CLAUDE.md "Device loop"). First is `forensics.sh`, because the crash
   history is already on the headset.
5. **Forensics before any play.** Pull `exit-info` and the dropbox crash entries. Classify past crashes:
   memory kill vs native crash vs timing (FINDINGS M9).
6. **Baseline with the official app, no code** (FINDINGS section 3):
   - the GC log via `custom_env.txt` (`JAVA_TOOL_OPTIONS=-Xlog:gc*:file=…`);
   - meminfo snapshots and `top -H`;
   - menu world on and off;
   - heap mode: read `Setting JVM memory` in `latestlog.txt`.

⏸ **Checkpoint:** crash classification, baseline numbers, heap mode, and whether Unity threads use CPU
while Minecraft runs.

---

## Vivecraft 1.3.15 base (2026-10-15)

The fork moved from QuestCraft's Vivecraft 1.2.5 to upstream Vivecraft 1.21.5-1.3.15 merged with
QuestCraft's OpenXR provider (CLAUDE.md, Repo layout). The Phase 1 and 2 toggles were ported onto it in
`integration/mihail.2`; the 1.2.5 work stays on `archive/vivecraft-1.2.5`. Before trusting any Phase 1/2
result on the new base, run the headset smoke test (`1.3.15-mihail.0`, then `mihail.2`): VR starts,
controllers work, a world loads, no crash in 10 minutes.

**ImmersiveMC** (`perf/immersivemc`): CI builds `immersivemc/` for Fabric against our Vivecraft jar and the
APK bundles it. The launcher installs it as `mods/ImmersiveMC-claudecraft.jar` at each launch; key
`immersivemc.install`, default **on** (off deletes that file). `[VCPERF] config` logs `immersivemc=<version or
absent>`. **Measure:** memory (`load`, `settled`, `long`) and frame time with it on vs off. ImmersiveMC's own
in-game config can also turn off single immersives.

**Register add-ons once** (`perf/api-register-once`, toggle `apiRegisterOnce`, default **on**): with ImmersiveMC,
CI logged `InteractModule 'immersivemc:bag_open' is already added` from `VRClientAPIImpl.processRegistrationEvent`
during the first resource load. Minecraft treats that as a failed initial load: it deselects every resource
pack and loads again (slower start, a second load peak). The toggle runs the registration handlers once and
logs `[VCPERF] api registration requested again` with a stack if something calls it a second time. If CI
still shows the duplicate error with the toggle on, ImmersiveMC added its handler twice instead.

---

## Phase 1 — Stability and memory (first sideload build, `mihail.1`)

All of these ship in one build, each behind its own toggle.

**Vivecraft** (`VRSettings`, on the "Performance and stability" page):

| Task | Toggle | Default | Finding |
|---|---|---|---|
| 1a `[VCPERF]` config line + periodic memory lines (heap, metaspace, code cache, direct, RSS; JVM args) | `perfMemoryLog` | on | M5, M7 |
| 1b Free the menu world when a world is joined, rebuild on the title screen; no menu-world loading/building while a world exists | `freeMenuWorldInGame` | on | V1 |
| ~~1c Stream-decode the menu world file~~ dropped on Vivecraft 1.3.x: upstream now inflates into one buffer and decodes in parallel | — | — | V1 |
| 1d Entity-outline targets shared by both eyes and created on first use | `shareOutlineTargets` | on | V2 |
| 1e Lazy auxiliary targets (camera, telescopes, keyboard), 1×1 mirror when the mirror is off | `lazyAuxBuffers` | on | V3 |
| 1f Supporter download: never keep `Player` objects; time out; fail cleanly | `fixSupporterLeak` | on | V4 |
| 1g Clear trackers' entity references on disconnect | `clearTrackerRefs` | on | V5 |
| 1h OpenXR startup: retry instance/session creation with a bounded wait instead of one fixed sleep | `xrStartupRetry` | on | V12b |
| 1i Honour `shouldRender`; idle instead of spinning when the session is not running | `xrSessionStateHandling` | on | V12 |

**Launcher** (keys in `claudecraft.properties`; the patched launcher writes the file with defaults on
first run):

| Task | Key | Default | Finding |
|---|---|---|---|
| 1j Heap policy: `-Xmx` = the custom RAM value if set, else 2048 MB; `-Xms512M`; ZGC `SoftMaxHeapSize` = 75% of `-Xmx`; `ZUncommitDelay=30` | `jvm.policy=claudecraft` | on | Q1, Q2 |
| 1k GC selectable (`zgc` / `g1`) for A/B | `jvm.gc` | `zgc` | Q1 |
| 1l GC log to `gc.log` (rotated) | `jvm.gcLog` | on | M2 |
| 1m Extra JVM args appended last | `jvm.extraArgs` | empty | — |
| 1n Keep the previous launcher log, don't wipe logcat, redact the access token | `log.keepPrevious`, `log.keepLogcat`, `log.redactToken` | on | M8, M9 |
| 1o Pause the Unity player once the game is up, and keep it paused across resume | `unity.pauseInGame` | on | Q4 |
| 1p Install the bundled Vivecraft jar into the instance at each launch | `vivecraft.installBundled` | on | — |

**Measure:** the protocol in CLAUDE.md, comparing the official app (baseline) with `mihail.1` defaults,
then single toggles off. The key numbers are crashes per hour, the `long` snapshot, the TOTAL peak at VR
start, and Allocation Stalls during join.

⏸ **Checkpoint:** results table for 1a–1p. Merge what measurably helped. Report any toggle whose "on"
caused a problem.

---

## Phase 2 — Smoothness (same build where possible)

| Task | Toggle | Default | Finding |
|---|---|---|---|
| 2a Frame timing: `xrWaitFrame` after the game tick, submit right after the right eye (QuestCraft `c4f3315`, already in the 1.3.15 base; off restores upstream's order) | `xrLateFrameWait` | on | V6 |
| 2b Refresh rate setting: MAX, 72, 90, 120. Request only if enumerated, apply live, log the actual rate | `xrRefreshRate` | 90 | V7 |
| 2c Cache OpenXR bound sources per action; refresh on interaction-profile change | `cacheInputOrigins` | on | V8 |
| 2d `glGetError` checks only when debugging | `glErrorChecks` | off | V9, L4 |
| 2e Linear filtering for Vivecraft's own quads despite LTW's LINEAR→NEAREST remap | `ltwLinearFilterFix` | on | L3 |
| 2f XR thread hint (`XR_KHR_android_thread_settings`) and CPU/GPU level hint (`XR_EXT_performance_settings`) | `xrPerfHints` | on | V13 |

**Measure:** frame-time median and p95, stale frames, CPU level, thermals over 20 min at 90 vs 120 Hz.

⏸ **Checkpoint:** results table; decide the defaults for the next build.

---

## Phase 3 — Shared culling across both eyes (main CPU win; design corrected)

Today Sodium redoes visibility for each eye *because the eye cameras differ*. The "always dirty" mixin
is a symptom, and removing it alone saves nothing (FINDINGS V11).

1. Build one **union frustum** for both eyes: HMD orientation, origin moved back
   `d = (IPD/2) / tan(min outer half-FOV)` (≈3 cm), and an off-centre projection from the per-side max
   tangents of both `XrFovf`. Feed it to Sodium's viewport and to vanilla entity culling for both eyes.
2. On the RIGHT pass of the same frame, skip Sodium's `markGraphDirty`/`update`, GFNI movement,
   `updateChunks`, `uploadChunks` and `tickVisibleRenders` (for example with `@WrapWithCondition`).
   Fall back to per-eye culling when the eyes are in different sections.
3. Optionally reuse the LEFT eye's visible-entity list for the RIGHT eye, and compute weather columns once.
4. Toggle `sharedEyeCulling`, default **on** (every feature defaults to on, user decision 2026-10-15).
   Pin the Mixin targets to Sodium 0.6.13 with `require` so a Sodium update fails loudly.

**Status (`perf/shared-eye-culling`, in `mihail.3`):** steps 1, 2 and 4 done; step 3 not done (entity
lists are cheap at the user's render distance; revisit if the `[VCPERF] cull` line says otherwise).
- The union frustum is built from both eyes' projection matrices and poses (works for any provider, not
  only `XrFovf`). The apex distance is the smallest that keeps every plane outside both eye apexes.
- It applies through a `WrapMethod` on `LevelRenderer.prepareCullFrustum`; the second eye pass cancels
  Sodium's `setupTerrain`.
- It falls back to per-eye culling for the frame when the eyes and the apex are in different sections,
  the eyes are not parallel, or a projection is unusable.
- Instead of `require`, the Sodium hook is optional. If it stops matching, the feature switches itself
  off and `[VCPERF] cull` reports `sodiumHook=false`. A hard failure would crash the game whenever
  QuestCraft's live `mods.json` moves Sodium.
- The `[VCPERF] cull` line (every 30 s) reports frames shared or per eye, fallback reasons, and Sodium
  `setupTerrain` time for the first and second eye pass. Compare it on vs off.
- CI smoke now really loads Sodium 0.6.13 (before, it was compile-only) and fails if no frame was shared.

**Validation:** no missing or flickering chunks at either eye's edges (fast head turns, cliffs, walls),
CPU frame time down, no GPU regression. Desktop pre-check with `nullvr` and the profiler (trust only the
CPU side).

⏸ **Checkpoint:** before/after table and the visual artifacts the user saw.

---

## Phase 4 — Research (propose before implementing)

- **Multiview (single-pass stereo)** is the only way to stop submitting every draw twice. It needs every
  vanilla and Sodium shader to use `gl_ViewID_OVR`, array targets, and an LTW bypass for those shaders
  (FINDINGS R1, L1). Ask the QuestCraft developers why the March 2025 attempt was dropped.
- **Render directly into the swapchain** when no post effect is active, plus `glInvalidateFramebuffer`
  on depth (V15).
- **Depth submission and space warp** feasibility under GL ES and LTW.
- **GC choice from data:** ZGC vs G1 using the `jvm.gc` toggle and the GC logs (Q1).
- **Unity:** `unload()` instead of `pause()` once the game is up (frees Unity's memory; risky because the
  JVM runs on a Unity-created thread). The microphone loop needs a Unity rebuild; meanwhile the user can
  revoke the microphone permission.
- **ModernFix for 1.21.5:** checked 2026-10-15: no 1.21.5 Fabric build found (official ModernFix stopped
  Fabric builds; the mVUS fork lists 1.21.6+). Dropped unless one appears.
- **Upstream cherry-picks:** menu world load speed-up (upstream `62535ff0`): already in the 1.3.15 base.

## Reporting format (every checkpoint)

1. What changed (branch, toggle, one line each).
2. The results table (from `perf/RESULTS.md`).
3. What the data says, including null or negative results.
4. What you propose next, and anything that needs the user's decision.
