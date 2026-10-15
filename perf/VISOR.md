# perf/VISOR.md: Visor compared with our Vivecraft fork

Visor (`visor-mod/`, VisorModStudio @ `89ebd958`, 0.6.0-beta, MPL-2.0) against our Vivecraft fork
(`vivecraft/`, 1.2.5 base `6d9b96af`). Its OpenXR layer is AtumVR (Phoenix-Ra, read at master `fee7d0a`;
Visor pins `1.14.0beta-snapshot#27`, which may differ slightly). This is a code reading only. Nothing
here was measured on the headset, and every MB figure is an estimate.

Paths: `V:` = `visor-mod/visor-core/src/main/java/org/vmstudio/visor/`, `V1215:` = the 1.21.5
overrides in `visor-mod/visor-core/mcversion/1.21.5/`, `A:` = AtumVR `atumvr-core/.../core/`,
`VC:` = `vivecraft/common/src/main/java/org/vivecraft/`.

## Verdict

- **Visor is smaller in scope, not lighter per frame.**
  - It has more code than Vivecraft: about 96k Java lines against 56k.
  - It has about as many mixins: 94 against 115 in the main config, 271 against 306 injectors.
  - It hooks the same hot paths.
  - Its savings come from features it doesn't have: no menu world, no supporter download, no
    telescopes, handheld camera or physical keyboard. Our Phase 1 toggles 1b, 1c, 1e and 1f already
    remove the same costs.
- **It renders the two eyes the same way we do.** Each eye runs a full `gameRenderer.render`
  (`V:core/client/provider/VisorScene.java:77-88,164`).
  - It skips the same second-eye work we already skip: light updates and `pick`.
  - It has no multiview, no shared culling and no shared chunk graph.
  - Its Sodium frustum is widened to cover both eyes plus 5° (`CullFrustumHelper:22-72`), but nothing is
    shared, so each eye draws *more* sections. Nothing in Visor reduces the draw-call cost; our routes
    there are still Phase 3 (V11) and R1.
- **It cannot run under QuestCraft.**
  - AtumVR supports only desktop GL (`XR_KHR_opengl_enable`, WGL/GLX/EGL-via-Monado) and Vulkan
    (`A:enums/XRGraphicsApi.java:17-18`, `A:session/opengl/XROpenGLBinding.java:394-411`).
  - It has no `XR_KHR_opengl_es_enable` and no Android loader init. Ours is in `VC:DeviceCompat.java:114-157`.
  - Its Vulkan bridge needs GL memory-object extensions that LTW doesn't provide.
  - Switching to it would mean porting our Android/GLES path into AtumVR, and we would still not gain
    per-frame speed.
- **It is worse than us in places:**
  - About 20–30 ungated `glGetError` calls per frame, with labels built eagerly. Vivecraft does about 9 (V9).
  - It fires one event per feature through a reflection event bus every tick and every frame.
  - Every visible overlay is redrawn every frame.
  - Empty `@WrapMethod` hooks on `Entity.move` run for every entity.

## What we can take from it

Each item would go behind its own toggle (CLAUDE.md rule 1). They are ranked by expected value for
our problem: crashes at about 6.3 GB, then CPU frame time.

| # | Idea | Visor evidence | Our target | Expected benefit | Risk | Relation to PLAN |
|---|---|---|---|---|---|---|
| X1 | **Don't give the swapchain wrapper targets depth.** Ours create each wrapper with `useDepth=true` (`VC:client_vr/provider/openxr/OpenXRStereoRenderer.java:61,64`); Visor wraps the images with no depth (`V:…/openxr/XrRenderTarget.java:15-27`). | as left | `OpenXRStereoRenderer` | ≈70 MB at 3 images per eye, 1680×1760, 32-bit depth (est; confirm the image count and the depth format) | Low, **if** nothing depth-tests into those targets. Check the post/FSAA copy into them first. | New. Not in FINDINGS; candidate 1q `swapchainNoDepth` |
| X2 | **Input pull model:** read each action once per frame with a reused `XrActionStateGetInfo` and subaction paths. No `xrEnumerateBoundSourcesForAction` in the read path; origins only for UI, cached, invalidated on `INTERACTION_PROFILE_CHANGED`. | `A:input/action/XRSingleAction.java:36`, `types/single/BoolButtonAction.java:34-57`, `XRState.java:82-84` | `MCOpenXR.readBoolean/readFloat/readVecData` (`:391,412,437,948-972`) | Removes hundreds of runtime calls per frame (V8 estimate 0.5–2 ms) | Low | Confirms 2c and improves on it: drop the calls instead of caching them |
| X3 | **Visibility-driven aux targets:** create when shown, free when hidden (with a delay against churn); outline targets only for passes that render, freed on `initOutline`/`close`. | `V:…/RenderTargetGUI.java:97-130`, `V:core/client/render/VRRendererBase.java:239-255`, `V1215:…/LevelRendererMixin.java:137-186` | `VRRenderer.setupRenderConfiguration`, `MultiPassTextureTarget` | Part of the 50–70 MB in V2/V3 | Low | Confirms 1d/1e; adds "free on hide" |
| X4 | **No mirror passes or targets when the window is 0×0** (always the case on the headset). | `V:…/VRRenderState.java:224-252` | `VRRenderer.getRenderPasses` | Small memory and CPU win if any mirror pass is active on device | Low | Fold into 1e |
| X5 | **`shouldRender` / view-validity gate:** skip acquire and all passes, submit an empty `xrEndFrame`; after 40 ticks hidden, open a pause screen. Unlike Visor, also throttle when not running and handle `LOSS_PENDING`. | `V:…/openxr/render/XrRenderer.java:111-166,264`, `V:core/client/VisorState.java:265-289` | `MCOpenXR.updatePose`, `VRPassHelper` | Heat and battery while the headset is off or a system dialog is up | Low | Template for 1i |
| X6 | **Orderly XR shutdown:** `xrRequestExitSession`, drain events for up to 500 ms, then `xrEndSession`. | `A:session/XRSession.java:222-284` | `MCOpenXR.destroy` | Hypothesis: cleaner relaunch after an exit (launch crashes are more likely right after a crash) | Low | Fits next to 1h |
| X7 | **One disconnect-cleanup hook** at `ClientPacketListener.close` TAIL. | `V:…/ClientPacketListenerMixin.java:38-42` | new mixin | Natural home for 1g | Low | 1g |
| X8 | **Hidden-area depth pre-mask** from `XR_KHR_visibility_mask`, drawn into depth after the clear. Vivecraft already has the `hiddenMeshVertices` plumbing (`VC:client_vr/provider/VRRenderer.java:69,160-173`); OpenXR never fills it. | `V:…/XrRenderer.java:340-372,472-540`, `V:…/render/helpers/RenderEffectsHelper.java:110-185` | `OpenXRStereoRenderer`, `LevelRendererVRMixin` | GPU fragment work only (≈10–20% fewer fragments); small while we're CPU-bound | Medium: LTW and Sodium interaction, and Quest runtime support for the extension is unverified | Phase 4 / V15 |
| X9 | Allocate projection views once, not per frame. | `A:rendering/XRRenderer.java:127-129` | `OpenXRStereoRenderer.java:81` | Tiny | None | V14 |

## What not to copy

- **Saving `DISABLED` after a failed VR start** (`V:core/client/VisorState.java:308-316`). Quest launches often succeed on the 2nd try,
  so this would turn one bad launch into VR staying off.
- **The startup busy-wait for READY** with no sleep and no timeout (`A:XRState.java:48-51`). Our 1h bounded retry is better.
- **The widened per-eye cull frustum.** It means more draws per eye; V11's design (shared frustum plus skipping Sodium's RIGHT update) stands.
- **Its ungated `glGetError` pattern, per-frame overlay redraws and the reflection event bus.**
- **Disconnecting from the world when the VR state changes** (`VisorState.java:105-109`).
- **The always-allocated window-size mirror target and per-pass outline targets.**
- **The refresh rate choice.** It picks the max rate too (marked "@TODO test it"), so it says nothing about 2b.

## Open questions before porting

- X1: list every write into `getLeftEyeTarget()`/`getRightEyeTarget()` and confirm that none has depth
  test or depth write on. Measure the meminfo `Graphics` row with the toggle on and off.
- X8: check that the Quest runtime exposes `XR_KHR_visibility_mask` (log the enumerated extensions),
  and how LTW handles the depth pre-pass.
