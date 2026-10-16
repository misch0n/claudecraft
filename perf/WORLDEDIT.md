# perf/WORLDEDIT.md: can we have WorldEdit and god mode on mihail.4?

Date 2026-10-16. Branch `perf/worldedit-feasibility`, from `integration/mihail.4` (build tag
`1.3.15-mihail.4`). Code reading and web research only, nothing tested on the headset.
WorldEdit source read at tag `7.3.14` (EngineHub/WorldEdit).

## Verdict

| | Feasible? | How | Work on our side |
|---|---|---|---|
| **God mode** | Yes, vanilla | Commands, with cheats on in the world | None |
| **WorldEdit** | Yes | Add WorldEdit 7.3.14 (Fabric, 1.21.5) as a user mod | None. Optional: bundle it (see "Option B") |

The only real risk is **memory during large edits**. We have no headset memory numbers for mihail.3 or
mihail.4 yet (test session 3 has not been run), so we can't say how much room there is below the
≈6.3 GB kill.

## God mode

No mod needed. Both commands need cheats on in that world: create the world with "Allow Cheats", or
use Open to LAN with cheats on.

- `/gamemode creative`: invulnerable, flight, unlimited blocks.
- To stay in survival but not die: `/effect give @s minecraft:resistance infinite 255 true` and
  `/effect give @s minecraft:saturation infinite 255 true`.

The commands are typed on the VR keyboard. Nothing in Vivecraft or our patches blocks commands.

## WorldEdit

### Which version (✔ from the source tags)

- **WorldEdit 7.3.14** is the newest release for 1.21.5. Tags 7.3.12–7.3.14 target 1.21.5; the next
  tag, 7.4.0, targets later Minecraft versions.
- Built against Fabric Loader 0.16.14 and Fabric API 0.126.0. The installed Fabric API 0.128.2 is newer,
  so it satisfies that.
- Required Fabric API modules: `api-base`, `command-api-v2`, `lifecycle-events-v1`,
  `events-interaction-v0`, `networking-api-v1`.
- `worldeditcui_protocol` is bundled inside the jar. `fabric-permissions-api` is only suggested.
- No other dependencies. License GPLv3.
- WorldEditCUI (client-side selection outlines) has a 1.21.5 build. It is optional.
- **Jar size: not verified.** Modrinth and CurseForge don't resolve from the container.

### How to install it (✔ in Pojlib on this branch)

Either path survives our patched launcher:

1. **QuestCraft's mod browser.**
   - The Unity UI calls `API.addExtraProject` (`pojlib/.../API.java:63`), which only records the entry
     (`InstanceHandler.java:232-249`).
   - The jar is downloaded at the next launch into `<instance>/mods/` (`MinecraftInstances.java:212-220,241`),
     only if the file is missing.
   - The mod search itself lives in the native Unity UI, so the Java side doesn't prove that the UI
     offers it.
2. **`adb push`** the jar into `<instance>/mods/`. Pojlib never touches jars it has no entry for, and
   Fabric loads them.

**Our patches don't interfere.** The 7 patched launcher classes (`apk/patched-sources.txt`) leave
`updateMods` alone. Each bundled-mod installer writes only its own file:
- `BundledVivecraft.java:96-105`
- `BundledImmersiveMC.java:32`, which writes `mods/ImmersiveMC-claudecraft.jar`.

**Ways it could be lost:**
- If the `mods/` folder is missing, `downloadAllMods` replaces the user entries with the manifest list
  (`MinecraftInstances.java:238,263-264`).
- If QuestCraft's `mods.json` ever adds a "worldedit" slug, the manifest takes over the entry
  (`:196-210`). If it later drops it, the jar is deleted (`:120-161`).

### Using it in VR (✔ code, ◐ behaviour inferred)

- WorldEdit's wand uses Fabric's `AttackBlockCallback` and `UseBlockCallback`. It acts only on the
  server side and only for the main hand (`FabricWorldEdit.java:328-330`).
  - In singleplayer the integrated server runs it, so nothing extra is needed on the client.
- **Vivecraft paths:**
  - Trigger/attack aimed at a block goes through vanilla attack, so it sets pos1.
  - Use aimed at a block (`BlockInteractionModule.java:107`, `MAIN_HAND`) sets pos2.
- ◐ **Accidental pos1:** roomscale swing mining (`SwingTracker.java:542`, `startDestroyBlock`) always
  counts as the main hand. Hitting a block with the off-hand controller while holding the wooden axe
  will probably set pos1 by accident.
  - Avoid it with `//pos1`/`//pos2`/`//hpos1`/`//hpos2`, or by turning off roomscale block breaking
    while editing.
  - An off-hand roomscale *use* (`SwingTracker.java:519`) is ignored by WorldEdit.
- ◐ Brushes and `//hpos` aim from the server-side player look. In VR that may not match where the
  controller points.
- No reported conflicts with Lithium, Sodium, FerriteCore, ImmersiveMC or Vivecraft. That is absence of
  evidence, not proof. WorldEdit's mixins are server-side only.
- No reports either way of WorldEdit on QuestCraft, PojavLauncher or Android.

### Memory (✔ mechanism from source, figures are estimates)

- **Undo history is in RAM only.** `LocalSession.history` is a transient list. Each entry stores old and
  new block state as packed primitives: positions in a `LongArrayList`, state IDs in int hash maps.
  - ≈40–60 B per changed block, both lists together (est).
  - Blocks with block entities are stored as full objects.
- **History keeps at least 15 edits.** `MAX_HISTORY_SIZE = Math.max(15, history-size)`
  (`PropertiesConfiguration.java:138`), so lowering `history-size` can't shrink it.
  - `//clearhistory` is the only way to free it.
- **Defaults are unlimited.** `default-max-changed-blocks`, `max-changed-blocks` and `max-radius` all
  default to -1.
- **During an edit** a temporary `BlockMap` of `BlockState` references is kept. That costs 8 B per
  reference with no compressed oops (our ZGC).
- **Clipboard:** `//copy` holds a dense `BaseBlock[]` the size of the selection (8 MB per million blocks).
- **Example:** a 100×100×100 `//set`, which is 1 M blocks (est):
  - ≈40–60 MB left in history, and a peak of ≈80–120 MB during the edit.
  - 15 of those in history: ≈0.6–0.9 GB of a 2 GB heap.
  - Plus native and GPU spikes from chunk relighting and Sodium remeshing, which these figures don't count.

**Recommended config** for our default 2 GB heap, in `<instance>/config/worldedit/worldedit.properties`:

```
default-max-changed-blocks=200000
max-changed-blocks=500000
max-radius=48
max-brush-radius=6
```

Run `//clearhistory` after big edits.

### Option B: bundle WorldEdit in our APK (not recommended yet)

This would copy the ImmersiveMC pattern:
- a `worldedit.install` key in `ClaudecraftConfig.java`;
- a `BundledWorldEdit` class, or `BundledImmersiveMC` generalised, whose duplicate check matches the
  slug "worldedit";
- one call at `JREUtils.java:208`;
- `apk/patched-sources.txt` and `apk/build-apk.sh --worldedit`;
- a CI step that downloads a pinned jar by URL and checks its sha256;
- `worldedit=` in `[VCPERF] config`.

**Why not now:**
- It would be the first prebuilt third-party jar we fetch, which needs a call on CLAUDE.md rule 5 ("no
  new dependencies").
- WorldEdit is GPLv3. Our GitHub releases would then redistribute it inside the APK.
- The user-mod path already works with zero code.

## To check on the headset

1. Install WorldEdit 7.3.14 through the mod browser (or adb) on mihail.4. Confirm `latest.log` lists it,
   and that `latestlog.txt` shows the bundled Vivecraft and ImmersiveMC installs unchanged.
2. Wand: pos1 with trigger, pos2 with use. Check the off-hand-swing pos1 case.
3. Memory: `tools/mem.sh` before and after `//set` on 50k, 200k and 500k blocks, then after
   `//clearhistory`. Record TOTAL and the `[VCPERF] mem` heap in `perf/RESULTS.md`.
4. Run test session 3 first, so there is a baseline for how close mihail.4 gets to 6.3 GB.
