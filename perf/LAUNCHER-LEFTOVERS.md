# perf/LAUNCHER-LEFTOVERS.md: what the launcher and title screen leave behind in game

Date 2026-10-16. Branch `claudecraft-unityless-launcher-exploration`, from `integration/mihail.4`.
**Research only:** code reading plus inspection of the released 6.0.0 APK (its dex, IL2CPP metadata, scene and
asset bundle). Nothing was measured on the headset.
- **✔** = verified in code or in the APK.
- **◐** = inferred.
- Every MB figure is an estimate.

**User decisions (2026-10-16):**
- Rewriting the launcher without Unity is possible (we have the recipe, see `perf/UNITYLESS-LAUNCHER.md`), but it is
  not the priority.
- The priority is to make the launcher layer as thin as possible once the game runs.
- Vivecraft stays as it is: one VR session from start to finish, where joining a world only changes what is shown.
- The title-screen environment only matters if it carries memory into the actual game.

## Answers

1. **The launcher layer.** Unity is the only large leftover: ≈150–300 MB (est) of scene, textures, swapchain and
   IL2CPP heap that stay resident after our `unity.pauseInGame`. Pojlib itself keeps only a few MB. Pojlib does
   have launch-time bugs: it re-downloads `client.jar` and rewrites the LWJGL jar on every launch.
2. **Vivecraft after a world is joined.** The title-screen menu world is freed correctly once the loading screen
   shows. There are two gaps, both before the world is playable (Gap A and Gap B below). In steady play nothing
   relevant is left over. Freed Java heap stays inside the process unless ZGC uncommits it.

## 1. The Unity layer after launch

Unity version 2022.3.60f1 ✔. The C# references below come from the wrapper repo (master `5aeb178`, which is newer).
The same methods, strings and GameObjects are present in the release's metadata and scene ✔.

**Still running every frame until our pause** (`unity.pauseDelayMs`, default 10 s after `gameReady`), and again
after any resume:
- **Three cameras render** ✔:
  - the XR main camera;
  - `DesktopCamera`, which `DesktopCam.cs:11` leaves active on Android;
  - the "Quester" mini-game camera, into a 600×1000 RenderTexture.
- **`InstanceButton.Update`** polls `gameReady` over JNI every frame, forever (`InstanceButton.cs:19-35`) ✔.
- **`LogReader`** ✔:
  - It watches `latestlog.txt` and re-reads the *whole* file on every change (`LogReader.cs:29-66`).
  - The game appends to that file all session, and the file has no size cap.
  - So the IL2CPP heap grows with the log (◐) even though the log panel is hidden.
- **Clock, keyboard and Quester `Update()` loops** ✔.
- **`Microphone.Start(loop)`**, never stopped (`JNIStorage.cs:47`) ✔. Whether it keeps recording while paused is ◐.

**Held in memory and never freed** (pause doesn't touch any of it):

| What | Estimate | Evidence |
|---|---|---|
| The WinterLodge scene: ≈45 lightmaps, 4k HDR skybox, meshes (`data.unity3d` is 49.9 MB uncompressed, 38.1 MB of it textures and meshes) | ≈40–50 MB | ✔ APK |
| Unity's window swapchain (3 Vulkan buffers) plus URP surface-sized targets (Medium quality, 4× MSAA) | ≈20–150 MB | ◐ |
| The Vulkan device, created through OpenXR before the scene loaded; `DeinitializeLoader` ends Unity's XR session but not the device | unknown | ◐ |
| IL2CPP heap (Boehm, non-moving), growing with every log re-read | tens of MB | ◐ |
| Mod and instance icons: textures never `Destroy()`ed, one texture per GIF frame (`GifLoader.cs:61`, `ModManager.cs:418`), web requests never disposed. Only present if the mod browser was used that session | 0 to tens of MB | ✔ |
| `libil2cpp.so` 33 MB, `libunity.so` 17 MB, `global-metadata.dat` 9.4 MB: mostly clean file pages the kernel can drop | — | ✔ |
| **Plausible total after launch** | **≈150–300 MB** | ◐ |

**What our Java side can do.** The C# can't be changed. The release's `UnityPlayer` has `pause()`, `unload()`,
`lowMemory()`, `quit()` and `destroy()` ✔ (dex). Unity's surface is unused by the game: Minecraft renders into its own
EGL pbuffer (`egl_bridge.c:138`), and input comes through the Activity ✔.

| # | Action (new `claudecraft.properties` key) | Expected | Risk | Measure |
|---|---|---|---|---|
| U1 | **Hide Unity's view after the pause** (`unity.hideViewInGame`): `setVisibility(GONE)` or remove the view. Unity then destroys its surface and swapchain, as it does when backgrounded | ≈20–150 MB | low–medium: nothing in the game draws to that surface ✔; confirm Vivecraft's XR session keeps focus | meminfo `Graphics`, `GL mtrack`, `EGL mtrack`, `TOTAL` at `settled`; `dumpsys SurfaceFlinger --list` |
| U2 | **Call `lowMemory()` just before `pause()`** (`unity.lowMemoryOnPause`). It is queued to Unity's main thread, so calling it after the pause probably does nothing. Today it fires only at `TRIM_MEMORY_RUNNING_CRITICAL` (`UnityPlayerActivity.java:439-446`) ✔, which may never arrive before the 6.3 GB kill | 0–30 MB | ≈none | `TOTAL`, `Native Heap` on/off |
| U3 | **`unload()` instead of `pause()`** (`unity.unloadInGame`): frees the scene, lightmaps, skybox and leaked icons. **Prerequisite:** `onUnityPlayerUnloaded` calls `moveTaskToBack(true)` (`UnityPlayerActivity.java:370-372`) ✔, which would push the game to the background; it must do nothing while in game. `CloseXR` on unload uses an already-null loader (`JNIStorage.cs:27-36`) and probably throws ◐ | ≈50–150 MB | medium–high. **Default off until tested** (an exception to rule 1's default-on) | as U1, plus `showmap` on libunity/libil2cpp, a `long` run, `forensics.sh` on any crash |
| U4 | Shorter `unity.pauseDelayMs`: cuts the window where all the loops above still run | CPU only | low: Unity's XR teardown must finish first | `top -H` (UnityMain, UnityGfx ≈0% after the pause) |
| U5 | Microphone loop: no Java fix. Revoking the microphone permission stops it, but breaks Simple Voice Chat | small | — | `dumpsys media.audio_flinger` after the pause |

## 2. The Pojlib layer after launch

**Held in memory after launch: a few MB, all harmless** ✔:
- `API` statics are strings and the account object; `profileImage` is a URL, not a bitmap.
- The MSAL4J client and its token cache.
- One Gson.
- The asset thread pool is shut down (`Installer.java:159-168`).
- `JRE.zip` is deleted after unpacking.

**Threads after launch:**
- **The C# launch thread** blocks inside `JLI_Launch` for the whole game (`InstanceButton.cs:65-78`, `jre_launcher.c`).
  Its stack holds the launch arguments, including the access token (a few KB).
- **The stdio pipe thread** (`stdio_is.c`) copies every JVM output line into `latestlog.txt`: one ART String per line,
  nothing accumulates ✔. The file is uncapped, and Unity's `LogReader` re-reads it (above).
- **The logcat reader** (`JREUtils.java:107-145`) runs a separate `logcat` child process (◐ 3–8 MB RSS) only for the
  `jrelog` and `LIBGL` tags. It also splits lines at 1 KB boundaries.

**Native side** ✔: fixed allocations only (`pojav_environ` ≈160 KB, including the 8000-entry event ring); no per-frame mallocs.

**Bugs** (✔ in our vendored source `c6566d5`; the 6.0.0 APK was built from ≈`092c27e`, so check the release's
`Installer` before patching):

| Bug | Where | Cost |
|---|---|---|
| **`client.jar` is deleted and re-downloaded whenever its SHA1 *matches*** (the check is inverted) | `Installer.java:71` | ≈28 MB download and seconds per online launch |
| **The LWJGL jar is rewritten every launch**: `sf == assetFile` compares array references, so it is always false; the jar is also read whole twice | `FileUtil.java:41-42`, `UnityPlayerActivity.java:126` | 3.4 MB of flash writes and ART garbage per launch |
| **Every launch re-verifies every asset**: ≈4000 SHA1s, the asset index re-fetched, a network `.sha1` per library, ≈8000 `new Gson()` | `Installer.java:113,149-183,216` | seconds of CPU and I/O at launch; a short-lived ART garbage peak |
| **A download can loop forever**: a non-200 response doesn't count as an attempt | `DownloadUtils.java` (retry loop) | possible launch hang |
| **The input queue is switched on but never drained**: `nativeSetUseInputStackQueue` is on (`UnityPlayerActivity.java:120`), but nothing on the Java side calls `pojavStartPumping`/`pojavStopPumping` | `input_bridge_v3.c:149,170` | queued key, char and scroll events are dropped (correctness, not memory) |
| `FileReader`s never closed; `DeleteGlobalRef` called on a jmethodID; a `pthread_create` check that can't fail | `GsonUtils.java:16`, `stdio_is.c:31,52,56` | hygiene |
| Every game exit kills the process (shutdown hook → `reinitUnity`), so `ClaudecraftUnityPause.onGameEnded`'s resume never runs | `CallbackBridge.java:41`, `UnityPlayerActivity.java:141-148` | none; it explains why resume is untested |

## 3. Vivecraft: what the title screen leaves behind in a world

**Freed correctly with `freeMenuWorldInGame` on** ✔. It runs on the first frame showing `LevelLoadingScreen` or
`ConnectScreen` (`MinecraftVRMixin.java:212-217`), with backstops in `handleLogin` and `checkTask`. It frees:
- the block, light, biome and heightmap data (`setWorld(null)`);
- every chunk vertex buffer;
- the native builders, after joining their futures.

A loader that hasn't started yet is skipped, and a running build is cancelled and dropped (`MenuWorldRenderer.java:685-709`).

**Nothing rebuilds it in-world** ✔: `init()` and `prepare()` refuse while a level exists, and the reload listener
needs `isReady()`. It uses no thread pool of its own (vanilla's background pool), so there are no thread leaks.

**Render targets are the same at the title screen and in-world** ✔. The keyboard and radial targets stay 2×2 and the
mirror 1×1 (`lazyAuxBuffers`). What's left after a join is under 1 MB: the sky VBOs and a lightmap.

**Gaps:**

| # | Gap | Size (est) | Fix (toggle `freeMenuWorldInGame`) | Risk |
|---|---|---|---|---|
| A | **A decode already running at join runs to completion** (full peak: compressed copy + inflated buffer + `BlockState[N]` + light arrays), then the result is dropped. The cancel flag is checked only before `loadWorld` (`MenuWorldRenderer.java:179`) ✔. It also competes with the world load for the same background pool | up to ≈2× the uncompressed world | pass the cancel flag into `MenuWorldExporter.loadWorld`; check it after inflating and after the block decode | low |
| B | **Singleplayer: the menu world is still resident during the server/datapack load**, before `LevelLoadingScreen` appears ◐ (vanilla's `WorldOpenFlows`; no client tick or `runTick` free runs in that phase) | ≈170–400 MB heap + its GPU buffers | also free at the start of `WorldOpenFlows` (open, create, load level) | low |
| C | **Every return to the title screen loads and builds a new random menu world**: the full peak again (`MenuWorldRenderer.java:203-210`). Nothing accumulates across cycles ✔ | one peak per return | keep the last world, or show none after the first join (new `menuWorldOncePerSession`) | low |
| D | With `physicalKeyboard=false` (not the default), the keyboard target grows to GUI size when used and never shrinks back (`VRRenderer.java:1150-1156`) | ≈7 MB GPU | resize to 2×2 on join (`lazyAuxBuffers`) | low |

**Does freed memory leave the process?** This decides whether anything from the title screen costs the world. The
claudecraft policy is `-Xms512M`, `-Xmx2048M`, ZGC with `-ZProactive`, `SoftMaxHeapSize=1536M` and
`ZUncommitDelay=30` (`JREUtils.java:221-265`).
- **ZGC reuses freed pages before it grows the heap.** The world load reuses the menu world's committed pages
  straight away. So it costs nothing in steady play, *provided* the in-game heap is at least as big as the title peak.
- **Uncommit returns memory to Android only for pages idle for 30 s** above `-Xms` ◐. It depends on the kernel
  supporting memfd hole-punching.
  - It is effectively off with the original custom-RAM mode (`-Xms = -Xmx`) ✔.
  - In the original auto mode, it waits 300 s and only goes down to 1024 MB.
- **Nothing forces a GC cycle** after the free, because of `-XX:-ZProactive` and `-XX:+DisableExplicitGC`. The garbage
  is collected only when allocation demands it.
- **GPU buffers** go back through LTW to the Adreno driver, which may cache them ◐. Compare the meminfo `Graphics` row
  at `title` vs `settled`.

**Is `menuWorldSelection=NONE` worth it?** Not for steady in-game memory. It still removes Gaps A, B and C and the
launch-time peak, which adds to the pressure that makes launches crash right after a previous crash. One A/B at the
`load` snapshot decides it. If `load` shows no difference, keep the menu world.

## 4. Inconsistencies found

1. **CLAUDE.md says ZGC's "committed heap stays resident".** That is true for the original modes but not under
   `jvm.policy=claudecraft`, where uncommit is on (30 s).
2. **`-XX:+UseCriticalJavaThreadPriority`** probably does nothing on Linux without `ThreadPriorityPolicy=1` ◐. The
   comment "Java should run at max" overstates it. `UseSignalChaining` is already the default.
3. **The wrapper repo's master is newer than the release** (project Unity 2022.3.62f3 vs the APK's 60f1). FINDINGS
   section 5 cites master line numbers.
4. **`latestlog.txt` has no size cap**, and Unity re-reads it whole on every change.
5. Already listed in `perf/UNITYLESS-LAUNCHER.md`:
   - the JRE is not pinned;
   - the odd manifest requirements;
   - `libEGL_dri.so`;
   - the mclo.gs log upload (token check);
   - FINDINGS being stale against mihail.4.

## 5. Recommended order (for the implementation session)

Each is its own toggle and gets its own launch with the same world and snapshots (CLAUDE.md protocol).

1. **Measure first:**
   - meminfo and `showmap` at `title`, `load` and `settled`, with `unity.pauseInGame` on, to size Unity's leftovers;
   - grep `gc.log` for `Uncommit: Enabled` / `Uncommitted`;
   - `top -H` after the pause.
2. **U1 hide Unity's view and U2 `lowMemory()` before pause:** cheap, Java-only, potentially the biggest win.
3. **Gap B and Gap A** in Vivecraft (`freeMenuWorldInGame`).
4. **Launcher bugs:** the inverted `client.jar` check, the LWJGL compare and the download loop. Add `Installer`,
   `FileUtil` and `DownloadUtils` to `apk/patched-sources.txt` after checking the release's versions.
5. **A GC nudge after the menu world is freed** (new toggle): drop `DisableExplicitGC` and call `System.gc()` once on a
   background thread, so uncommit can return the memory. Measure the committed heap at `settled`.
6. **U3 `unload()`** last, default off, only if U1+U2 leave a large Unity footprint.
7. **Optional:**
   - stop the logcat child once VR starts;
   - cap `latestlog.txt` (also cuts Unity's re-read cost before the pause);
   - `menuWorldOncePerSession`.
