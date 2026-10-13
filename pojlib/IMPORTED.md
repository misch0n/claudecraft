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
