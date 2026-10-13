# PLAN.md — work plan

Phases run in order. Within a phase, each task is its own branch, toggle and `perf/RESULTS.md`
entry. Stop and report to the user at every **⏸ checkpoint**.

---

## Phase 0 — Setup and baseline (no code changes)

1. **Find the Minecraft version on the headset.** Use adb to list the QuestCraft instance folder
   and the jar names in its `mods/`. Record the Minecraft version, the exact Vivecraft jar
   filename, and the Sodium version actually shipped (later Mixins target that exact Sodium).
2. **Check out the matching branch** (`OpenXR-<mcversion>`) as `main`. Add remotes:
   - `qc` → `https://github.com/QuestCraftPlusPlus/VivecraftMod`
   - `upstream` → `https://github.com/Vivecraft/VivecraftMod` (its `openxr` branch is newer; cherry-pick only)
3. **Build unmodified** with the tag `-mihail.0`. Write the `tools/` scripts (see `CLAUDE.md`).
4. **Prove jar replacement works:** deploy `mihail.0`; the user launches; confirm the tag appears
   in `latest.log`. Add the `[VCPERF]` config log line in this build.
5. **Baseline run** with the measurement protocol. Also run once with the menu world **disabled in
   VR Settings** (existing option, zero code); the difference sizes Task 1b.

⏸ **Checkpoint:** report the version, paths, baseline numbers, and the meminfo breakdown (Java
Heap vs Native vs Graphics). If Graphics or Native dominates by far, tell the user before
continuing; it may re-rank the phases.

---

## Phase 1 — Quick wins (small, low risk)

### 1a. Refresh-rate setting
- Today `MCOpenXR.initDisplayRefreshRate()` requests the **highest** enumerated rate (120 Hz on 3S).
- Add the setting `xrRefreshRate`: `MAX` (current behaviour), `72`, `90`, `120`. Request the
  chosen rate only if the runtime enumerates it, else fall back to `MAX`. Apply on change without
  a restart if possible (`xrRequestDisplayRefreshRateFB` works mid-session).
- Log the rate actually in effect (`xrGetDisplayRefreshRateFB`) in the `[VCPERF]` line.
- **Default: 90.** This is the one deliberate default change; keep `MAX` selectable.
- **Measure:** frame-time consistency (p95, dropped frames) at 90 vs 120, plus thermals over 10 min.

### 1b. Free the menu world during gameplay
- `MenuWorldRenderer` buffers and its `FakeBlockAccess` (full block, light and biome arrays) stay
  resident in-game; `destroy()` frees buffers but keeps `blockAccess`.
- Setting `freeMenuWorldInGame` (default **on**): on level join call `completeDestroy()` and drop
  `blockAccess` and any other large references. Rebuild via the normal `init()`/`prepare()` path
  when returning to the title screen. Make sure the builder threads are cancelled first.
- **Measure:** `settled` and `walk` meminfo vs Phase 0 baseline and vs the "menu world disabled" run.

### 1c. Lazy auxiliary framebuffers
- `VRRenderer.setupRenderConfiguration` always allocates: two 720² spyglass targets with depth,
  1920×1080 hand-camera + camera-render targets with depth, GUI-size keyboard and radial-menu
  targets with depth + mipmaps, and a mirror target.
- Setting `lazyAuxBuffers` (default **on**): allocate spyglass, camera, keyboard and radial
  targets on first use; free them after N seconds unused. Trace every reader for null-safety,
  including `resize` paths and `WorldRenderPass` statics. Size the mirror target to 1×1 when the
  mirror is not shown.
- **Measure:** Graphics row of meminfo. Expect tens of MB, not GBs; report the honest number.

⏸ **Checkpoint:** results table for 1a–1c. Merge whatever measurably helped.

---

## Phase 2 — Shared culling across both eyes (the main CPU win)

**Problem:** each eye runs the full world render. `SodiumWorldRendererVRMixin` forces Sodium to
rebuild its chunk visibility graph on **every pass**, so the occlusion search, entity and
block-entity collection and particle setup all run twice per frame for cameras ~6 cm apart.

**Design**
1. On the first stereo pass of a frame, build a **cull camera** whose frustum encloses both eye
   frusta: midpoint of the eyes, horizontal FOV = union of left and right eye angles, apex moved
   back so both eye apexes are inside. (Conservative: may draw slightly more, never less.)
2. Run Sodium's graph update once with that camera; on the second eye **skip** the update and
   reuse the render lists. Replace the "always dirty" Mixin with "dirty on first pass only" under
   a setting `sharedEyeCulling` (default **off** until validated).
3. Leave per-eye draw submission alone (that part genuinely differs per eye).
4. Optional second step, separately toggled: reuse the visible entity / block-entity lists for the
   second eye, filtered by the combined frustum.

**Validation (all required)**
- No missing or flickering chunks at the edges of either eye: test fast head turns, looking down
  cliffs, and standing next to walls (worst case for occlusion from the midpoint).
- CPU frame time down on OVR Metrics, no GPU regression.
- Pin the Mixin targets to the Sodium version recorded in Phase 0; use `expect`/`require` so a
  Sodium update fails loudly rather than silently.

**Desktop pre-check:** before headset testing, run on the Mac with the `nullvr` provider to verify
nothing crashes and to compare pass timings with the Minecraft profiler. The desktop GL path
differs from the headset's GL ES path, so trust only CPU-side conclusions from it.

⏸ **Checkpoint:** before/after table, and a list of any visual artifacts the user saw.

---

## Phase 3 — Research items (only after Phases 1–2; propose before implementing)

- **Render directly into the swapchain** when no post effects are active (no vignette, FOV
  reduction, water/portal overlay), skipping the per-eye full-screen copy. On a tile-based GPU
  this also saves a resolve per eye. Also a prerequisite for fixed foveated rendering to affect
  world rendering at all.
- **Submit depth** (`XR_KHR_composition_layer_depth`) for better reprojection; then assess
  **Application SpaceWarp** (`XR_FB_space_warp`) feasibility under GL ES and the translation layer.
  Report feasibility only.
- **Allocation profiling:** Java Flight Recorder on the Mac with `nullvr`, to find per-frame
  allocation hot spots in Vivecraft's render path (garbage → collection pauses → stutter).
- **Launcher-side (separate repo, `Pojlib`):** Java runtime flags (GC choice, metaspace and
  direct-memory caps, heap uncommit). Out of scope for this repo; write a recommendation with data.

## Reporting format (every checkpoint)

1. What changed (branch, toggle, one line each).
2. The results table (from `perf/RESULTS.md`).
3. What the data says, including null or negative results.
4. What you propose next, and anything that needs the user's decision.
