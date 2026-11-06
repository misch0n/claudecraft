# Port to Minecraft 26.3 with Vulkan (2026-11-06)

**Goal:** Minecraft 26.3 on the Quest 3S, rendered through Mojang's **Vulkan** backend. Vivecraft comes
from **upstream's 26.3 build**, with QuestCraft's Android/OpenXR layer carried on top of it. The
launcher (Pojlib) is updated for Java 25 and for SDL3 windowing. The OpenGL + LTW path stays available
as a fallback toggle.

This is an experiment. The milestones are ordered so the riskiest unknowns come first.

## Target stack

| Layer | Today (claudecraft-1) | Target |
|---|---|---|
| Minecraft | 1.21.5 | 26.3 (unobfuscated) |
| Renderer | OpenGL → LTW → GLES | **Vulkan** (Mojang's `com.mojang.renderpearl.backend.vulkan`), with OpenGL + LTW as a toggle |
| VR mod | QuestCraft's Vivecraft 1.2.5 fork | **upstream Vivecraft `Multiloader-26.3`** (1.3.18) + our port of QuestCraft's OpenXR provider |
| OpenXR binding | `XR_KHR_opengl_es_enable` (EGL handles from `libvloader`) | `XR_KHR_vulkan_enable` (Vulkan handles from Minecraft's device); GLES binding kept for the fallback |
| Java | OpenJDK 22 | OpenJDK 25 |
| Window/input | Pojlib's GLFW stub (`pojlib/jre_lwjgl3glfw`) | an SDL3 implementation (see C) |
| Fabric | Loader 0.16.10 | Loader 0.19.5, API 0.160.4+26.3 (from upstream Vivecraft's `gradle.properties`) |

## What we found in the code

### Upstream Vivecraft 26.3 already supports Vulkan

Branch `Multiloader-26.3` @ `15a9011` (2026-11-05, "bump to 1.3.18") has Fabric, Forge and NeoForge;
`java_version=25`, `fabric_loom=1.15-SNAPSHOT`.

- `GraphicsHelper` picks `OpenGLHelper` or `VulkanHelper` depending on Minecraft's active backend
  (`FrontendGpuDevice.backend` is a `GlDevice` or a `VulkanDevice`).
- `VulkanHelper` already provides what an OpenXR Vulkan binding needs: the `VkInstance`,
  `VkPhysicalDevice`, `VkDevice`, graphics queue and queue family pointers, plus `VkImage` handles of
  Minecraft textures. It also has image-layout transitions and a `vkCmdBlitImage` helper, and records
  one-off command buffers through Minecraft's command encoder.
- Mixins on `VulkanInstance` and `VulkanBackend` add the instance and device extensions the VR runtime
  asks for. They are stored in `VRSettings.requiredVulkanInstanceExtensions` / `...DeviceExtensions`.
  The first run detects missing extensions, saves them and asks for a restart, because Minecraft
  creates its Vulkan device before VR starts.
- Only the **OpenVR** provider uses all this. Upstream has **no OpenXR provider**.
- Upstream already moved its input code to SDL (`SDLScancode`, `SDLKeyboard`, `SDLMouse`).

### QuestCraft's layer is small and self-contained

Comparing QuestCraft's fork (`6d9b96af`, our import `1809965`) with upstream tag `1.21.5-1.2.5`, the
QuestCraft-only code is:

| Code | Lines |
|---|---|
| `client_vr/provider/openxr/` (`MCOpenXR`, `OpenXRStereoRenderer`, `DeviceCompat`, `XRBindings`, haptics, util) | ~1,970 |
| `client_vr/provider/control/` (action sets, `VRInputAction` moved out of OpenVR, swipe and haptic helpers) | ~740 |
| `util/VLoader.java`, JNI into Pojlib's `libvloader`: EGL context/config/display, Dalvik VM, activity | 14 |
| Hooks in shared code: `VRState` (`case OPENXR -> new MCOpenXR`), `MCVR` (uses `DeviceCompat`) | a few lines |

There are also about 50 small edits in shared files. Some are QuestCraft's and some are upstream drift
between `1.2.5` and the fork's base. Each one needs a look when porting, but most should already be in
upstream 1.3.18 or no longer matter.

**All the graphics-specific code sits in `DeviceCompat` and `OpenXRStereoRenderer`.** For Vulkan we
add a `Mobile`-Vulkan variant:
- `getGraphicsExtension()` → `XR_KHR_vulkan_enable`. Use version 1, because Minecraft creates its own
  `VkInstance` and device; version 2 wants the runtime to create them.
- `checkGraphics()` → `XrGraphicsBindingVulkanKHR` filled from `VulkanHelper`'s pointers.
- Swapchain images become `XrSwapchainImageVulkanKHR`. Each eye is blitted into the acquired swapchain
  image with `VulkanHelper`'s blit and barriers. Zero-copy (wrapping the swapchain images as Minecraft
  textures) can come later.
- The required extensions come from `xrGetVulkanInstanceExtensionsKHR` / `...DeviceExtensionsKHR`,
  fed into upstream's existing "save and restart" path. A later improvement is to create the
  OpenXR instance before Minecraft's Vulkan init, which would avoid the restart.
- Vulkan needs no EGL getters from `VLoader`, only the Dalvik VM and activity, so this path does not
  depend on LTW at all.

### The launcher side (Pojlib)

- The 6.0.0 APK's native libraries (JRE 22, LWJGL 3.3.3 natives, LTW, `libpojavexec`, `libvloader`)
  come from the released APK; we have never built native code. 26.3 needs:
  - **JDK 25 for Android aarch64.** I found no ready build: Zalith's build repo is for Java 8, and the
    26.2 experiment packages one but its build process is not documented. Most likely we build it in
    CI with the NDK, using the same patch set Pojav-family launchers use.
  - **LWJGL natives for the version 26.3 ships.** That includes SDL3 (or a stub), shaderc if the
    game ships it (one third-party note says 26.3 compiles shaders with Shaderc on both GL and
    Vulkan), plus OpenAL, stb, freetype and whatever else its version JSON lists. Vulkan itself is
    Java-only in LWJGL and loads `/system/lib64/libvulkan.so`.
- **The window.** Pojlib replaces `org.lwjgl.glfw.GLFW` with a Java stub that has no OS window. 26.3
  calls SDL3 instead, and on Vulkan it also wants a `VkSurfaceKHR` + swapchain for the desktop window
  (`SDL_Vulkan_CreateSurface`). Options:
  1. A Java stub of the SDL calls Minecraft makes, like the GLFW stub. We need the exact call list from
     the 26.3 jar first.
  2. Real SDL3 built for Android with the `offscreen` video driver (no `SDLActivity`, which would clash
     with Unity's activity).
  3. Either of the above, plus a mixin that skips presenting the window in VR. Vivecraft renders eyes
     into its own targets anyway, so the window swapchain could be 1×1 or headless.
- `pojlib/src/main/assets` / `MCOptionUtils`: write Minecraft's graphics-backend option from a
  `claudecraft.properties` key (Rule 1). Keycode tables (`LwjglGlfwKeycode`, gamepad map) move to SDL
  scancodes, and mouse buttons become 1-based.
- `mods.json` is fetched live from upstream's `QuestCraft-6.0.0` branch and only knows 1.21.5, so we
  need our own. `supportedVersions.json` needs 26.3. Open question: will the native Unity UI (which we
  can't change) list and create a 26.3 instance?

## Plan

Each milestone either ends in something we can check, or stops the experiment.

**M0: toolchain and facts (CI only, no headset).**
- Import upstream `Multiloader-26.3` @ `15a9011` as a git subtree. Add a CI job with JDK 25 that builds
  its Fabric jar.
- In CI (which can reach Mojang), read 26.3's version JSON and record the LWJGL version and every
  native library. Run `javap` on the client jar and list every `org.lwjgl.sdl` call, plus what the
  Vulkan backend needs from the window. Also confirm the graphics-backend option name in `options.txt`.
- Check Modrinth for 26.3 builds of Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling and
  MoreCulling.

**M1: QuestCraft's OpenXR provider on upstream 26.3 (code; it compiles, untested).**
- Port `provider/openxr`, `provider/control` and `VLoader` onto the new subtree: renderpearl API
  changes, SDL input.
- Keep the GLES binding, and add the Vulkan binding described above.
- Add a `VRSettings` toggle for the OpenXR graphics binding, and the `[VCPERF]` line (Rules 1 and 2).

**M2: 26.3 reaches the title screen on the Quest, flat, no VR (the big risk).**
- Build JDK 25, the LWJGL/SDL3 natives and the SDL window shim. Patch Pojlib to launch 26.3.
- Try OpenGL + LTW first: it changes less, and LTW is already proven. Then switch the backend toggle
  to Vulkan.
- Kill criteria: no working JDK 25 build, or an SDL shim that needs more than about 3 weeks.

**M3: VR on Vulkan.** OpenXR session with the Vulkan binding, eyes blitted into the swapchain. Then
re-apply our perf toggles from claudecraft-1 where they still apply (menu world, outline targets, aux
buffers, refresh rate, late frame wait). Then the measurement protocol, comparing against claudecraft-1.

**Do the 1.21.5 lessons carry over?** The claudecraft-4 black screen (Vivecraft 1.3.15 on 1.21.5)
happened at the OpenXR hand-off with QuestCraft's code. M3 meets the same hand-off, so the
`perf/TEST-BLACKSCREEN.md` notes apply.

## Open questions

- Is Vulkan in 26.3 an option you can switch to? Reports say yes ("Default / OpenGL / Vulkan
  (experimental)"), with OpenGL as the default until 26.4. M0 confirms it from the jar.
- Does the Quest's Vulkan driver (Adreno 740) expose what the backend requires (Vulkan 1.2, dynamic
  rendering, push descriptors) and what the runtime asks for? Check on the headset with
  `vulkaninfo`, or from Minecraft's log once M2 runs.
- Does the Unity launcher accept a 26.3 instance?
- Fabric builds of Sodium and the other mods for 26.3 (Sodium has 0.9 betas for 26.2, already on
  Blaze3D).

## Sources

- Upstream Vivecraft: https://github.com/Vivecraft/VivecraftMod (branch `Multiloader-26.3`)
- Fabric 26.1: https://fabricmc.net/2026/03/14/261.html, https://docs.fabricmc.net/develop/porting/
- 26.3 and SDL3: https://www.gamingonlinux.com/2026/09/minecraft-java-edition-26-3-wilderness-bound-update-released/, https://www.minecraft.net/en-us/article/minecraft-26-3-snapshot-4
- Vulkan: https://www.minecraft.net/en-us/article/minecraft-26-2-snapshot-1, https://syntaxmine.com/articles/minecraft-26-4-snapshot-1
- SDL3 on mobile launchers: https://github.com/catsruledogs/Amethyst-iOS-25/issues/29, https://github.com/minizyz/glfw-restored
- 26.2 Quest port (reference): https://github.com/necat101/questcraft-26.2-experiment
- Upstream QuestCraft: https://github.com/QuestCraftPlusPlus/Pojlib, https://github.com/QuestCraftPlusPlus/VivecraftMod
- JDK builds for Android: https://github.com/ZalithLauncher/Android_OpenJDK_Build
