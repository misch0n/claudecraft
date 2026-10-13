# pojlib/ (vendored)

Source of QuestCraft's launcher core, imported from
`https://github.com/QuestCraftPlusPlus/Pojlib` at commit
`c6566d540a3ef530017f99b59f24d38121e3a5df` (branch `QuestCraft-6.0.0`, 2026-09-02).

Excluded on import:
- `libs/unity-classes.jar` (Unity's proprietary classes; not ours to redistribute)
- all `*.so` files (they are Git LFS pointers upstream; the APK pipeline takes
  the real native libraries from the released QuestCraft APK instead)
- `.gitmodules` (the `wrapper` submodule is not needed)
- the LFS rule in `.gitattributes`

We do not build this as an AAR. `apk/` compiles only the classes we change and
patches them into the released QuestCraft APK (see CLAUDE.md, "Build").

## The released APK is older than this source

The QuestCraft 6.0.0 APK was built on 2025-05-31 (its `assets/lwjgl/version` is `1748715506389`), so it
matches Pojlib at about `092c27e` (2025-05-30), not the branch head. For the classes we replace
(`apk/patched-sources.txt`), the only later upstream change is `874e85b` (2025-06-09, "Fix ram setter") in
`JREUtils.java`. We reverted it here, so the heap sizing below our toggles is the one the APK ships:
`-Xmx4096M` when more than 4096 MB is free at launch, `-Xmx2048M` from 2048 to 4096 MB, `-Xmx1536M` below
(always `-Xms1024M`). The build's patch report checks this: "shipped JREUtils reads MemoryInfo.threshold: 0".

Other differences between the APK and this source that matter to us:
- `options.txt` in the APK has `renderDistance:5` and `simulationDistance:5` (this source has 4 and 4, from
  `3cefa91`, 2025-06-02).
- `mods.json` is not affected: the APK downloads it from the `QuestCraft-6.0.0` branch at every launch.
