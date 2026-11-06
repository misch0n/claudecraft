## 9. Lessons from Visor (added 2026-10-15)

Visor (`visor-mod/`, VisorModStudio `89ebd958`, 0.6.0-beta) is another OpenXR VR mod. It is often
described as lighter than Vivecraft. The full comparison with file references is in `perf/VISOR.md`.
Code reading only; nothing measured.

**Summary.** ✔
- **Smaller in scope, not lighter per frame.**
  - About 96k Java lines against our 56k, and 271 mixin injectors against 306.
  - It hooks the same per-frame and per-tick paths we do.
  - It uses less memory only because it has no menu world, no supporter download, and no telescopes,
    handheld camera or physical keyboard. Phase 1 (1b, 1c, 1e, 1f) already removes those costs from ours.
- **Renders the eyes like we do:** a full `gameRenderer.render` per eye, with only lighting and `pick`
  skipped on the second eye. It has nothing that cuts draw calls; R1 and V11 remain the only routes.
  - Its Sodium frustum is widened to both eyes plus 5°, with nothing shared, which is *more* draws per eye.
- **Cannot run under QuestCraft.** Its OpenXR library (AtumVR) has no `XR_KHR_opengl_es_enable` and no
  Android loader init, and its Vulkan bridge needs GL memory-object extensions LTW lacks.

**V16. The swapchain wrapper targets allocate depth buffers that are never used.** ✔ (new; found via Visor)
- **Where:** `OpenXRStereoRenderer.java:61,64` creates every `VRTextureTarget` around a swapchain image with
  `useDepth=true`.
- **Never used:** the only write into these targets is the post-process pass. It goes through
  `ShaderHelper.renderFullscreenQuad`, whose `createRenderPass(colorTexture, OptionalInt.empty())` uses
  colour only (`ShaderHelper.java:59-82`, called at `:249`). Every reader uses colour or the size: the
  mirror blit (`ShaderHelper.java:269-308`) and the resolution label (`VRSettings.java:1848`).
  Visor wraps the same images with no depth (`XrRenderTarget.java:15-27`).
- **Estimate:** ≈71 MB at 3 images per eye, 1680×1760, 32-bit depth (est). Log `imageCount` to confirm.
- **Fix:** pass `useDepth=false`. Toggle `swapchainNoDepth`, default on. Proposed PLAN item 1q; risk low.

**Things Visor does better, worth porting.** Each still needs its own toggle.
- **Input** (◐, AtumVR source): reads each action once per frame with a reused
  `XrActionStateGetInfo` and subaction paths. It never calls `xrEnumerateBoundSourcesForAction` in the read
  path, and refreshes on `INTERACTION_PROFILE_CHANGED`. This is a stronger form of V8 / PLAN 2c: remove
  the calls rather than cache them.
- **Session state** (✔): when `shouldRender` is false or the views are invalid, it skips acquire and all
  passes and submits an empty `xrEndFrame`. After 40 ticks hidden, it opens a pause screen. This is the
  template for V12 / PLAN 1i. Visor itself still spins after STOPPING and ignores `LOSS_PENDING`, so ours
  must cover those too.
- **Render targets** (✔): overlay targets are created when shown and freed when hidden. No mirror passes
  exist while the window is 0×0, which is always true on the headset (pbuffer, V3). Outline targets are
  freed on `initOutline`/`close`. These extend V2/V3 / PLAN 1d, 1e; free after a delay to avoid churn.
- **Shutdown** (◐, AtumVR): `xrRequestExitSession`, then drain events for up to 500 ms, then `xrEndSession`.
  It may help relaunch after an exit (hypothesis, M9). Fits next to 1h.
- **Disconnect cleanup** (✔): one hook at `ClientPacketListener.close` TAIL. This is the place for 1g (V5).
- **Hidden-area mask** (✔): reads `XR_KHR_visibility_mask` and draws the hidden mesh into depth after the
  clear. Our `hiddenMeshVertices` plumbing exists, but OpenXR never fills it. GPU only, so it belongs with
  V15; first check that the Quest runtime lists the extension.
- **Projection views** (◐, AtumVR): allocated once, not per frame (V14). Tiny.

**Do not copy.** ✔
- Saving VR as DISABLED after one failed start. On the Quest the 2nd launch usually works.
- An unbounded busy-wait for READY.
- The widened per-eye frustum.
- About 20–30 ungated `glGetError` calls per frame, worse than V9.
- A reflection event bus fired per feature every frame.
- Per-frame overlay redraws.
- Empty `@WrapMethod` hooks on `Entity.move`.
- Disconnecting from the world when the VR state changes.
- Max refresh rate (marked "@TODO test it" upstream, so it tells us nothing about V7).

**Proposed PLAN changes (need the user's OK):**
- Add 1q `swapchainNoDepth` (V16).
- Widen 2c to the pull model.
- Add free-on-hide and the 0×0 mirror rule to 1e.
- Use Visor's `shouldRender` gate plus a pause screen for 1i.
- Add XR shutdown order to 1h.
