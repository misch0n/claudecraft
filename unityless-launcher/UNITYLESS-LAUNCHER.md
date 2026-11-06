# perf/UNITYLESS-LAUNCHER.md: a QuestCraft launcher without Unity

Date 2026-10-16. Branch `claudecraft-unityless-launcher-exploration`, from `integration/mihail.4`.
**Research only:** code reading, the released 6.0.0 APK's manifest, and web research. Nothing was built or
tested on the headset.
- **✔** = verified in code or in a primary source.
- **◐** = inferred.
- Meta's developer site was blocked from the container, so Meta facts marked ✔ come from Meta's own GitHub
  samples. Anything else from Meta is a search snippet (◐).

## The goal

1. **No Unity runtime** in the app.
2. **The launcher is a flat window over passthrough.** It keeps QuestCraft's features, laid out in a more
   functional way.
3. **The Minecraft title screen and options are a flat window over passthrough.** No virtual environment
   (menu world, panorama, room); full VR starts only once a world is joined.
4. **The pre-world phase is as light as possible.** Once Minecraft runs, nothing of the launcher stays in
   memory.

## Verdict

**Feasible, with work.** It is a new app shell, not a patch.

- **Unity is only the UI and a temporary XR session.** Login, instances, mods, downloads and launch are all
  Pojlib Java. Unity calls them by polling static fields; there are no callbacks into C#.
- **Minecraft never needed Unity's window.** It renders into an offscreen EGL pbuffer (`egl_bridge.c:138`),
  and Vivecraft opens its own OpenXR session with any Activity Pojlib hands it.
- **The cost is that we stop patching QuestCraft's APK and build our own.**
  - We would compile Pojlib from our vendored source, without its Unity classes.
  - We would reuse the non-Unity native libraries.
  - We would add a small native-Android launcher.

| Goal | Verdict | Main work |
|---|---|---|
| 1. No Unity | Feasible with work | New Activity replaces `UnityPlayerActivity` (input, clipboard, restart, LWJGL install); own APK build |
| 2. Flat launcher over passthrough | Feasible, with a caveat | A plain Android 2D panel activity. Passthrough behind it depends on the user's home setting (see inconsistencies) |
| 3. Flat title screen over passthrough | Feasible with work | `XR_FB_passthrough` in our OpenXR provider; title screen as a flat window with no environment |
| 4. Nothing of the launcher after launch | Mostly feasible | Unity's ≈50 MB of native code plus its Vulkan/XR state disappear; the panel activity is finished. The JVM can't be restarted in-process, so going back to the launcher means a process restart (as today) |

## Proposed shape: one APK, two activities, one process

Meta calls this a **hybrid app** (✔ Meta's HybridSample manifest, Horizon OS v69+). Both activities run in the
same process, which the in-process JVM requires (◐ standard Android with no `android:process`; Meta doesn't
document it).

```
[Launcher panel activity]        2D window in the home environment (passthrough home → over passthrough)
  category com.oculus.intent.category.2D, plain Android Views, no XR, no GL
  account · instances · mods (Modrinth) · settings · log  ──  Play
        │ prelaunch() on a worker thread, then start the game activity, then finish() the panel
        ▼
[Game activity]                   immersive, category com.oculus.intent.category.VR, singleTask
  input + clipboard bridge (moved from UnityPlayerActivity), VLoader.setAndroidInitInfo(this)
  API.launchInstance() → JLI_Launch → Minecraft + Vivecraft (OpenXR, GLES via LTW, pbuffer)
        │
        ├─ title screen: passthrough layer + flat GUI window, no eye passes   (Vivecraft change)
        └─ world joined: normal VR; passthrough paused
  game exit → JVM shutdown hook → relaunch the panel + kill the process (today's restart path, retargeted)
```

**Switching between the two activities (✔ Meta sample):**
- Panel → immersive: `startActivity(Intent(this, GameActivity).setAction(MAIN).addFlags(FLAG_ACTIVITY_NEW_TASK))`.
- Immersive → panel: a `CATEGORY_HOME` intent with the extra `extra_launch_in_home_pending_intent`.

## 1. Removing Unity: what it does today and what replaces it

**Pojlib API the new launcher calls** (`pojlib/API.java`) ✔. The UI reads results by polling, exactly as Unity does now.

| Area | Calls |
|---|---|
| Instances | `loadAll` :109, `load` :120, `createNewInstance` :145 (and the `.mrpack` variant :159), `deleteInstance` :132, `getQCSupportedVersions` :101 |
| Mods | `addExtraProject` :61, `hasExtraProject` :82, `removeExtraProject` :96; progress `isDownloadsCompleted` / `getDownloadPercentage` :65-71 |
| Launch | `prelaunch` :176 (worker thread), then `launchInstance` :201 (blocks for the whole game), `restartLauncher` :210 |
| Account | `login` :234, `removeAccount` :220; poll `msaMessage`, `currentAcc`, `profileName/UUID/Image`, `isDemoMode` |
| Settings (static fields) | `memoryValue`, `customRAMValue`, `developerMods`, `model` (becomes env `VR_MODEL`, `JREUtils.java:166`) |
| Other | `hasConnection` :271; `gameReady` |

**Login is all Java and works without a webview** (✔ `pojlib/account/LoginHelper.java:53-96`, `Msa.java`).
- It uses MSAL4J's **device-code flow**. The launcher shows `API.msaMessage` (a code to enter at microsoft.com/link)
  and polls `currentAcc`.
- Tokens: `USER_HOME/cache_data/serialized_cache.json`. Accounts: `files/accounts/<uuid>.json`.

**Unity-bound code that must move into the new game activity** ✔:
- `UnityPlayerActivity` extends a Unity lifecycle interface and owns `mUnityPlayer` (:30-31, :96).
- Its `onCreate` does:
  - `Constants.initConstants` :88;
  - `nativeSetUseInputStackQueue` :120;
  - key, gamepad and mouse forwarding into `CallbackBridge` (:219-345).
- `installLWJGL` is called from `JREUtils.java:198` and `InstanceHandler.java:185`. It lives on the Unity class,
  so it must move.
- The clipboard: `awt_bridge.c:41` looks up `pojlib/UnityPlayerActivity.GLOBAL_CLIPBOARD`. Either keep that class
  name or change one native lookup.
- The restart path: a JVM shutdown hook (`jre_lwjgl3glfw/.../CallbackBridge.java:41`) runs `restartUnitySession` →
  `reinitUnity` (`UnityPlayerActivity.java:141-148`). That relaunches the app and kills the process.
- `ClaudecraftUnityPause` (our `unity.pauseInGame`) becomes unnecessary.

**The fixed 2.5 s sleep goes away** (`DeviceCompat.java:125-127`) ✔.
- It waits for Unity to see `API.gameReady`, fade out and call `DeinitializeLoader()` (`InstanceButton.cs:25-33`).
- With no Unity XR session there is nothing to wait for.
- Keep a toggle anyway (CLAUDE.md rule 1), since this is the suspected cause of intermittent launch crashes (V12b).

**Native libraries in the 6.0.0 APK** ✔:

| Status | Libraries |
|---|---|
| Unity, dropped | `libunity` (16.8 MB), `libil2cpp` (32.9 MB), `libmain`, `libUnityOpenXR`, `ConformanceAutomationExt`, `libmock_api`, `libmock_runtime`, `libopenxr_runtime_debugger`, `assets/bin/Data` (scenes, assets) |
| Pico, dropped | `libopenxr_pico`, `libPICO_TOBAPI`, `libpxrplatformloader` |
| Reused | `libpojavexec(_awt)`, `libawt_headless`, `libawt_xawt`, `libistdio`, `libjrelauncher`, `libvloader`, `libltw`, `liblwjgl*`, `libopenal`, `libopenxr_loader`, `libc++_shared`, `libjnidispatch`, `libsqlitejdbc` |
| Owner unclear ◐ | `libwebp*`, `liblog.so` |

The JRE itself is downloaded at first run (`Installer.java:36`), not shipped.

**Licenses** (✔ LICENSE files on GitHub):
- LGPLv3: Pojlib, LTW.
- GPLv2+CE: the OpenJDK mobile builds.
- BSD-3: LWJGL.

Unity and QuestCraft's Unity-compiled code are the only proprietary parts, and they are what we drop. Personal
use only, as now.

## 2. The launcher as a flat window

**A plain Android 2D activity runs as a panel in the Quest home environment** (◐ Meta docs snippets; default
1024×640 dp).
- It needs no GL, no XR and no Unity.
- Built with plain Android Views it adds no new dependency (rule 5).
- A WebView or Compose UI would be heavier.

**Features to keep** (the full Unity feature list, ✔ from the wrapper's C#):

| Feature | Keep? | Note |
|---|---|---|
| Legal-acceptance screen | Keep | First run only |
| Accounts: add (device code), switch, remove, demo mode, avatar | Keep | Enumerate `files/accounts`, not Unity's `launcher.conf` |
| Instances: create (name, MC version, loader, default mods), list with images, delete, remember last | Keep | |
| Modrinth browser: mods, resource packs, data packs, modpacks; filter by version and Fabric; auto-resolve required dependencies; per-instance installed list; remove | Keep | Modrinth HTTP calls move from C# to Java |
| `.mrpack` modpack import | Keep | Pojlib already handles it (`API.java:159`) |
| Settings: custom RAM toggle and value, developer mods | Keep | Plus our `claudecraft.properties` keys, which today have no UI |
| Download progress | Keep | |
| Live `latestlog.txt` view, upload to mclo.gs | Keep the view | Upload: check token redaction first (inconsistency 9) |
| Changelog/news, version check | Decide | |
| Discord/X/website links | Decide | |
| VR keyboard | Not needed | A 2D panel gets the system keyboard |
| "Quester" mini-game | Drop | |
| Kill/restart button | Keep | |

**A more functional layout** (a proposal to discuss):
- **One screen, two columns.** Left: the account chip and the instance list. Right: the selected instance, with
  tabs for Mods, Settings and Log, and one large **Play** button.
- **The Modrinth browser** is a tab on the instance, not a separate scene.
- **Download progress and errors** show inline on the Play button.
- **Our perf keys** get a "Performance" section in Settings, so `claudecraft.properties` no longer needs adb.

## 3. The title screen as a flat window over passthrough

**Today** (✔ `VRPassHelper.java:46,190-223`, `VREffectsHelper.java:600-662`): with no world loaded, each eye still
runs the full `gameRenderer.render`, drawing:
- the menu world, or the panorama cube when it is off (`menuWorldFallbackPanorama=true`), or the dirt room;
- the GUI quad;
- the 3D physical keyboard (`physicalKeyboard=true`);
- hands and controllers.

Every environment clears to opaque alpha. The default `menuWorldSelection=BOTH` (`VRSettings.java:532`) loads the
menu world: 170–400 MB of heap, plus a decode peak (V1, est).

**Starting with VR off until a world is joined is not viable** (✔/◐).
- With VR off, vanilla draws to the 0×0 pbuffer, so nothing is visible.
- Hot-switching follows the XR session state, not world join (`MinecraftVRMixin:221-226`).
- The title screen therefore needs the OpenXR session, and the "flat window" is a layer in it.

**Passthrough is available to us** (✔).
- `FB_passthrough` exists in LWJGL 3.3.3, including the QuestCraftPlusPlus fork we build against.
- Our provider requests no passthrough extension today (`MCOpenXR.java:860-897`).
- Its extension list `callocPointer(7)` is full; grow it to 8.
- We submit one opaque projection layer (`OpenXRStereoRenderer.java:156-195`).
- The manifest needs `<uses-feature android:name="com.oculus.feature.PASSTHROUGH">`. Our own APK can declare it.

**Options, ranked:**

| # | Mode | Memory (est) | CPU per frame | Effort | Risk |
|---|---|---|---|---|---|
| 0 | `menuWorldSelection=NONE` (setting only) | −170–400 MB heap | 2 eyes × panorama, GUI, keyboard, hands | none | none |
| 1 | **Hybrid:** projection layer kept, environment skipped, eyes cleared to alpha 0, GUI quad + hands, passthrough layer underneath | as 0 | slightly below 0 | S–M | low–medium: alpha must survive the post-process copy (`ShaderHelper.java:186`), and the swapchain format must have alpha (`GL_SRGB8_ALPHA8` is first choice, `MCOpenXR.java:1071`) |
| 2 | **Quad only:** passthrough + one `XrCompositionLayerQuad` with the GUI texture, no eye passes | as 0, and ≈ −85 MB more if the eye swapchains and "3D Render" target are created only at world join (+≈11 MB quad swapchain) | one GUI blit | M–L | medium: frame-loop branches (`VRRenderer.getRenderPasses:429`, `OpenXRStereoRenderer.setupRenderConfiguration/endFrame`, `VRPassHelper.renderAndSubmit:179-267`), lazy eye-target creation at join |

**Notes on option 2:**
- **Input keeps working.** It is ray-cast against the GUI plane with no rendering involved (`VRPlayer.postPoll:138` →
  `GuiHandler.processGui`).
- **What is lost:** the hands, the controller models and the 3D keyboard. Text fields need the 2D keyboard as a
  second quad.
- **The window's size must match the ray maths** (`GuiHandler.java:258-289`, the 1.5×scale factor).

**Recommendation:** ship option 0 at once, build option 1 as the first passthrough step, then option 2 if the
headset numbers justify it. Each goes behind its own `VRSettings` toggle.

**On world join:**
- pause passthrough (`xrPassthroughPauseFB`);
- create the eye targets if they were deferred;
- move the GUI back to in-world placement.

**On return to the title screen:** restart passthrough, and skip the menu-world reload
(`MenuWorldRenderer.java:203-210`).

**Passthrough cost** (◐ Meta snippets): the system composites it outside our process. Meta calls it "expensive"
on Quest 3, and while it is on, GPU levels 3–4 are unavailable. That only matters on the title screen, since it
pauses in-world.

## 4. What stays in memory after launch

| Today | Unity-less |
|---|---|
| Unity's IL2CPP code (≈50 MB of `.so`), its managed heap, Vulkan device, scene and textures, its XR loader state; paused by our `unity.pauseInGame` but still resident | Gone. The launcher panel is `finish()`ed; its Views and window are freed (◐ small: a few MB of ART objects) |
| Microphone loop from Unity `Start` (`JNIStorage.cs:47`, never stopped) | Gone. Request `RECORD_AUDIO` for Simple Voice Chat at launch instead |
| Fixed 2.5 s wait, then Unity tears down its XR session | No second XR session, no wait |
| ART and Pojlib classes | Stay (they host the JVM). Small. |

**Nobody has measured what Unity holds.** That number decides how much this whole project is worth, so measure
it before implementation (test A below).

## Inconsistencies

**In the goals:**
1. **"Launcher over passthrough"** — a 2D panel shows over passthrough only if the user's home environment is
   set to passthrough. An app can't force it for a panel (◐ Meta snippet). The alternative is an immersive
   launcher drawing a quad over a passthrough layer. That one is always over passthrough, but it needs XR and GL
   before the JVM, which works against "as light as possible". Recommendation: the panel; set the home to
   passthrough once.
2. **"Clean up all traces of QuestCraft once we launch"** — the launcher UI and Unity can go. Pojlib (the launcher
   core) can't, because it is what starts and hosts the JVM. HotSpot can't be started twice in one process, so
   returning to the launcher after a game always restarts the process. It does that today too.
3. **"Vivecraft puts us in a virtual environment; unnecessary"** — Vivecraft must still own the XR session at the
   title screen (VR-off shows nothing). The fix is what Vivecraft draws there, not removing Vivecraft before the world.
4. **"Retain the functionality"** — some functionality is Unity-only and has to be rewritten in Java: the Modrinth
   browser, the account list, `launcher.conf`, legal acceptance and the log upload.

**In our docs and code:**

5. **Wrapper master is newer than the 6.0.0 release** (✔ dex and IL2CPP metadata). It calls `mirrorNativesInFolder`,
   `unzipSavesFromFolder` and `fixDataPermissions`, which neither our Pojlib nor the APK have. FINDINGS section 5
   cites master line numbers as if they describe 6.0.0.
6. **The JRE is not pinned** (✔ `Installer.java:36` downloads `releases/latest/download/JRE.zip`, once).
   CLAUDE.md says `jre22-6.0.0`. Our own APK should pin a JRE.
7. **Every game exit kills the process** (✔ shutdown hook → `reinitUnity` → `killProcess`). The resume path of
   `ClaudecraftUnityPause.onGameEnded` almost never runs.
8. **The manifest declares odd requirements** (✔ release APK):
   - eye tracking `required=true` and Vulkan required;
   - Pico meta-data repeated about 60 times;
   - target SDK 32;
   - Unity mock-runtime and debugger libraries shipped.
   
   `pojlib/src/main/AndroidManifest.xml` is only a library stub; the real manifest exists only in the APK.
9. **The mclo.gs upload sends `latestlog.txt`** (◐). Check whether that file can contain the access token before
   keeping the feature (CLAUDE.md rule 9).
10. **Possible RAM-setting race** (◐ `ConfigHandler.LoadConfig` vs the `DevHandler.Start` listener). The custom RAM
    value may be ignored at startup. It disappears with the rewrite.
11. **`-Dorg.lwjgl.egl.libname=/system/lib64/libEGL_dri.so`** (`JREUtils.java:280`) points at a library that
    probably doesn't exist on Quest (◐).
12. **Unity starts with `-androidChainedSignalHandlerBehavior=disabled`** (`UnityPlayerActivity.java:83`). Without
    Unity, the JVM's signal flags (`-XX:-ImplicitNullChecks`, Q3) may no longer be needed (◐).
13. **FINDINGS is stale against mihail.4:**
    - V1 cites `MenuWorldRenderer:604` and "nothing tears down"; `freeMenuWorldInGame` exists.
    - V2/V3 describe allocations that `shareOutlineTargets`/`lazyAuxBuffers` already fix.
    - V12 says `shouldRender` is never read; it is (`OpenXRStereoRenderer.java:91`).
    - V13 says 5 extensions; there are 7.
    - Z1 assumes the dirt room is the fallback; the panorama is.

## Build pipeline consequences

- **Today:** `apk/build-apk.sh` patches 7 classes into QuestCraft's APK. A Unity-less app needs a real Android
  build in CI. CI can reach the Android SDK (this container can't).
- **New build:**
  - Pojlib from `pojlib/` without `UnityPlayerActivity`/`ClaudecraftUnityPause`;
  - `jre_lwjgl3glfw`;
  - the new launcher and game activities;
  - the reused `.so` files extracted from the pinned 6.0.0 APK, as now;
  - our Vivecraft and ImmersiveMC jars;
  - our own manifest;
  - signed with the same debug key.
- **The package name decides the data folder.** Use the `side` package (`com.qcxr.qcxr.claudecraft`) so it
  installs next to the official app. Accounts in `files/accounts` don't carry over between packages, so log in again.
- **CLAUDE.md's Build section** would need rewriting when this is implemented.

## Unknowns that need the headset, and the test for each

| Test | What it answers | How |
|---|---|---|
| A. Unity's resident memory | How much the whole project saves | Official app: `dumpsys meminfo` and `showmap` (`libunity`, `libil2cpp`, Vulkan/`Graphics` rows) on the launcher screen, and again 60 s after the title screen appears with `unity.pauseInGame` on |
| B. Hybrid switch with a live JVM | Whether the two-activity design works | Minimal APK: 2D panel starts a VR activity that calls `xrCreateSession` (no Minecraft). Check focus, the process survives, return to the panel |
| C. Passthrough cost | Whether option 1/2 is worth it | Title screen with option 0 vs option 1: OVR Metrics GPU level, CPU/GPU frame time, meminfo `TOTAL` |
| D. Alpha through the pipeline | Whether option 1 needs shader changes | Clear an eye to alpha 0 with passthrough on and look for the room |
| E. Home environment | Whether the launcher panel shows over passthrough | Set the home to passthrough, open any 2D app |

## Suggested order for the implementation session

1. **Option 0 now:** set `menuWorldSelection=NONE` as our default. It is a setting change, so measure it with the
   protocol.
2. **Test A** (Unity's footprint) with the current build. This decides whether steps 4–6 are worth their effort.
3. **Passthrough title screen, option 1,** in Vivecraft behind a toggle. It works inside the current
   QuestCraft APK once the manifest feature is added: `side` decodes the manifest; `replace` needs a pipeline change.
4. **Test B** with a throwaway two-activity APK.
5. **Unity-less shell:**
   - the game activity first, with the Unity-less build and a minimal Play-only panel;
   - then port the launcher features;
   - then remove the 2.5 s wait.
6. **Option 2 (quad-only)** if test C shows that the eye passes still matter at the title screen.
