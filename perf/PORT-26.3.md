# Can we port to Minecraft 26.3? (2026-11-06)

Short answer: **it can be done, but it is a new project, not an upgrade.** Moving from 1.21.5 to 26.3
replaces four layers at once: the Java runtime, the window library, the LWJGL natives and the Vivecraft
base. Today we only patch Java classes inside the released 6.0.0 APK. Nobody upstream has done the
hardest part, the SDL3 window backend on Android. My estimate: **6–12 weeks of focused work, with a real
chance of getting stuck on SDL3.** 26.2 is the cheaper first target (it still uses GLFW).

## What changed between 1.21.5 and 26.3

| Change | When | Effect on us |
|---|---|---|
| Unobfuscated jars; Fabric moved to the `net.fabricmc.fabric-loom` plugin (no mappings, no remap) | 26.1 (Mar 2026) | Every mod has to be rebuilt; nothing for 1.21.x loads. Our build needs Loom 1.15+ and Gradle 9.x. Mojang names actually make mixins easier to write. |
| **Java 25 minimum** | 26.1 | QuestCraft ships `jre22-6.0.0`, so we need an aarch64 Android JDK 25 build. Upside: JDK 25 has compact object headers (`-XX:+UseCompactObjectHeaders`), a direct heap-memory win for our 6.3 GB limit. |
| Vulkan renderer (opt-in) | 26.2 snapshots; default from 26.4 snapshots | 26.3 still defaults to OpenGL, so LTW keeps working. Vulkan needs 1.2 + dynamic rendering + push descriptors; the XR2 Gen 2 (Adreno 740) should support that. Longer term this could replace LTW altogether, but Vivecraft's OpenXR code uses the GLES binding. |
| Sodium 0.9 moved all rendering onto Mojang's Blaze3D | 26.2 betas | The multi-draw → single-draw problem in LTW may change, for better or worse. We'd have to measure it again. |
| **GLFW → SDL3** for windowing and input; LWJGL ships `lwjgl-sdl`, not `lwjgl-glfw` | 26.3 snapshot 4 (Jul 2026) | **This is the blocker.** Pojlib fakes the window with its own GLFW stub (`pojlib/jre_lwjgl3glfw`, 21 files, ~14k lines, plus native `CallbackBridge`). For 26.3 that has to be redone as an SDL3 shim on LWJGL 3.4.x. Key and mouse codes also change: SDL scancodes, and mouse buttons numbered from 1. |

## Who has already done what

- **QuestCraftPlusPlus (upstream):** nothing for 26.x. Pojlib has one branch, `QuestCraft-6.0.0`, whose
  newest version is 1.21.5 (last commit Sep 3, 2026). Their VivecraftMod fork has only `1.21.0`.
- **Upstream Vivecraft:** 26.1.2 builds exist (1.3.10–1.3.15), and CurseForge lists `26.3-1.3.17` (seen
  as NeoForge; I could not confirm a Fabric jar because Modrinth is blocked from this container). It does
  not have QuestCraft's Android OpenXR provider.
- **`necat101/questcraft-26.2-experiment`:** a community port of the whole stack to **26.2**: Pojlib,
  the Unity wrapper (rebuilt with Unity 2022.3.62f3), Vivecraft, JDK 25, LTW as the fallback renderer and
  an experimental Vulkan path. It calls itself experimental, and its controller bindings are not updated.
  This is the best reference we have, and it suggests 26.2 can be reached.
- **SDL3 on mobile launchers:** nobody has a working version. The Amethyst-iOS effort (LWJGL 3.4.1 +
  SDL3) never compiled, and that repo was archived on Oct 19, 2026. `glfw-restored`, a mod that puts
  GLFW back on 26.3, is only a proof of concept: it can't attach to the window or route input.
- **Mods we use:** Sodium has 0.9.0 betas for 26.2 but no 26.3 build that I could find. I could not check
  Lithium, FerriteCore, ImmediatelyFast and the rest (Modrinth is unreachable from here). There is no
  ModernFix today either, so nothing changes there.

## The work, layer by layer

1. **Build pipeline (new).** We have never built native code. 26.x needs us to build, or source from
   somewhere, a JDK 25 for Android aarch64, LWJGL 3.4.x natives (core, OpenGL/GLES, `lwjgl-sdl`), SDL3
   for Android, and Pojlib's own `libpojavexec`/GLFW-stub replacement. That means the Android NDK in
   GitHub Actions. Our current "patch the released APK" approach still works for the Unity layer.
2. **Java 25 on Android.** We could reuse a JDK 25 Android build from another launcher (Mojo, Zalith,
   PojavLauncher forks) or from the 26.2 experiment, then check it under `JLI_Launch` inside the Unity
   process. Moderate risk.
3. **Window and input (SDL3).** This is the critical path. There are two options:
   - (a) implement the SDL3 functions Minecraft calls as a stub, in the same way Pojlib stubs GLFW today;
   - (b) a Fabric mixin that sends Minecraft's window and input calls back through our existing GLFW stub
     (the `glfw-restored` idea, but our stub needs no OS window, which makes it easier than on desktop).

   (b) is likely the smaller and more rebase-friendly change, because Vivecraft owns input in VR anyway.
   The risk is high either way; budget 2–4 weeks.
4. **Vivecraft.** Start from upstream Vivecraft `26.3-1.3.17` (Fabric), port the QuestCraft OpenXR
   provider onto it (`MCOpenXR`, `OpenXRStereoRenderer`, `DeviceCompat`, `VLoader`), then re-apply our
   perf toggles (29 commits on `vivecraft/`). Note that claudecraft-2–4 already tried a 1.3.x base on
   1.21.5 and ended in a black screen (`perf/TEST-BLACKSCREEN.md`). The same OpenXR hand-off problem
   will come back here, so fixing it on 1.21.5 first is useful groundwork. 2–3 weeks.
5. **Launcher (Pojlib).** Add 26.3 to `supportedVersions.json`, Fabric Loader 0.19.x, and a `mods.json`
   we host ourselves (the upstream one is fetched live and only knows 1.21.5). Version-specific code
   would also need updating: `MCOptionUtils`, and the keycode tables (`LwjglGlfwKeycode`, gamepad map) for
   SDL scancodes. One open question: does the native Unity UI (which we cannot change) accept a
   version it doesn't know? 1 week, plus the risk from that question.
6. **Mods.** Sodium (26.2 beta now; wait for 26.3), Lithium, FerriteCore, ImmediatelyFast,
   EntityCulling, MoreCulling, Fabric API 0.161+. Whatever doesn't exist yet gets dropped for the first
   build.
7. **Measure.** Run the full protocol again. 26.x has more content, and Blaze3D changes how Sodium draws,
   so the memory and frame-time baselines from 1.21.5 do not carry over.

## Risks, ranked

1. **SDL3 shim**: nobody has done it on Android; the iOS attempt failed.
2. **Native toolchain**: a whole new kind of CI work for this repo (NDK, JDK 25, LWJGL 3.4).
3. **Unity launcher** (native, we can't change it) may not offer or create a 26.3 instance.
4. **Vivecraft 1.3 on QuestCraft** has already shown a black screen.
5. **Mod availability** for 26.3, Sodium first.
6. **Memory**: newer versions usually need more. JDK 25 compact headers may offset some of it.

## Recommendation

Go in stages, each a headset build, so the work stops being worth it at a clear point if it fails:

1. **Fix the claudecraft-4 black screen on 1.21.5** (Vivecraft 1.3 base). This is needed for any port.
2. **JDK 25 on 1.21.5** (1.21.5 runs fine on Java 25). It tests the runtime swap on its own and gives
   us compact object headers now, behind a `claudecraft.properties` toggle.
3. **26.2** (GLFW still in use): use the 26.2 experiment as the reference. This proves unobfuscated
   Fabric, Blaze3D Sodium and Vivecraft 26.x on the Quest.
4. **26.3**: the SDL3 shim. Do it only once 3 works, and only if 26.3's content matters to you more
   than the 4–8 weeks it takes. Waiting has a benefit too: once 26.4 makes Vulkan the default, a direct
   Vulkan + `XR_KHR_vulkan_enable2` path could remove LTW (and its draw-call cost) altogether, which
   would be a bigger performance win than anything left on 1.21.5.

Unverified (sites blocked from the container): a Fabric jar of Vivecraft 26.3, Sodium 26.3, the
exact LWJGL version 26.3 ships, and its GL minimum. Check those on Modrinth/minecraft.wiki before
starting step 3.

## Sources

- Fabric 26.1 announcement and porting guide: https://fabricmc.net/2026/03/14/261.html, https://docs.fabricmc.net/develop/porting/
- 26.3 release and SDL3: https://www.gamingonlinux.com/2026/09/minecraft-java-edition-26-3-wilderness-bound-update-released/, https://www.minecraft.net/en-us/article/minecraft-26-3-snapshot-4
- Vulkan: https://www.minecraft.net/en-us/article/minecraft-26-2-snapshot-1, https://syntaxmine.com/articles/minecraft-26-4-snapshot-1
- SDL3 on mobile: https://github.com/catsruledogs/Amethyst-iOS-25/issues/29, https://github.com/minizyz/glfw-restored, https://github.com/soymods/pathmind/pull/91
- 26.2 Quest port: https://github.com/necat101/questcraft-26.2-experiment
- Upstream state: https://github.com/QuestCraftPlusPlus/Pojlib, https://github.com/QuestCraftPlusPlus/VivecraftMod
- Vivecraft 26.x: https://www.curseforge.com/minecraft/mc-mods/vivecraft/files/8161675
- Sodium 26.2: https://modrinth.com/mod/sodium/versions
