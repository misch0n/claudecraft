# immersivemc/ (vendored)

ImmersiveMC (hammy275), imported from `https://github.com/hammy275/immersive-mc` at commit
`8713ae83ecf8816850b1bcb8c19dca72025c997b` (branch `1.21.5`, 2026-09-30, `mod_version=1.6.0-beta1`).
License: LGPLv3 (`LICENSE.txt`).

Excluded on import: `docs/*.gif` (about 120 MB of demo animations the README links to).

Status: **built and bundled.** CI (job `immersivemc`) builds the Fabric jar against our own Vivecraft
jar (1.21.5-1.3.15 from `vivecraft/`), the APK carries it as `assets/claudecraft/ImmersiveMC.jar`, and
the patched launcher installs it as `<gameDir>/mods/ImmersiveMC-claudecraft.jar` at every launch
(`claudecraft.properties` key `immersivemc.install`, default on; off deletes the file). QuestCraft's
`mods.json` has no ImmersiveMC, so nothing else installs or removes it.

Our changes (normal commits on top of the import):
- `settings.gradle`: includes only the platforms listed in `enabled_platforms`.
- `gradle.properties`: `enabled_platforms=fabric`; `mod_version=1.6.0-beta1+claudecraft.3` (bump the
  suffix when we change the code); `loom.ignoreDependencyLoomVersionValidation=true` (our Vivecraft jar
  is built with a newer Loom, as `vivecraft/gradle.properties` already allows).
- `common/build.gradle`, `fabric/build.gradle`: with `-Pvivecraft_jar=<jar>`, Vivecraft is a file mod
  dependency (`modApi files(...)`) instead of `curse.maven:vivecraft-667903`. Without it the build
  is upstream's.
- `DevVRServerAPI` (dev-only stub) implements `hasVivecraft` and `getVivecraftVersion`, which Vivecraft's
  `VRServerAPI` gained in 1.3.13; upstream compiles against an older Vivecraft. The other dev stubs
  already match the 1.3.15 API interfaces.

Build: `cd immersivemc && ./gradlew :fabric:build -Pvivecraft_jar=<path to our Vivecraft fabric jar>`
(JDK 21) → `fabric/build/libs/immersivemc-<mod_version>.jar`.

Runtime needs: Fabric API (`depends: fabric`; a QuestCraft core mod) and Minecraft. Vivecraft is optional
for ImmersiveMC but always present here. Mod Menu, Lootr and Inventory Profiles Next are optional
integrations. Nested jars: see the CI `report-immersivemc` artifact.

Same rules as the rest of the repo: measure memory and frame time with it on and off before keeping it.
