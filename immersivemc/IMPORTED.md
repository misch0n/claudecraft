# immersivemc/ (vendored, parked)

ImmersiveMC (hammy275), imported from `https://github.com/hammy275/immersive-mc` at commit
`8713ae83ecf8816850b1bcb8c19dca72025c997b` (branch `1.21.5`, 2026-09-30, `mod_version=1.6.0-beta1`).
License: LGPLv3 (`LICENSE.txt`).

Excluded on import: `docs/*.gif` (about 120 MB of demo animations the README links to).

Status: **parked.** Not built by CI and not bundled into the APK yet. It is here so the
integration work (bake it into our QuestCraft build next to Vivecraft) starts from a fixed
source. Our changes go in as normal commits on top of this import.

Notes for the integration task:
- It builds against Vivecraft's API from CurseForge (`curse.maven:vivecraft-667903`, file ids in
  `gradle.properties`); about 40 classes use `org.vivecraft.api`. Point it at our own jar
  (Vivecraft 1.21.5-1.3.15, `vivecraft/`) instead.
- Upstream builds Fabric, Forge and NeoForge; we only need Fabric (`enabled_platforms`).
- QuestCraft's `mods.json` is fetched live at every launch, so the APK has to install the
  jar itself (as `vivecraft.installBundled` does for Vivecraft).
- Same rules as the rest of the repo: every change behind a toggle, measure memory and frame time
  with it on and off before keeping it.
