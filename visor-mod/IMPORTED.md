# visor-mod/ (vendored, reference only)

Visor (VisorModStudio), imported from `https://github.com/VisorModStudio/Visor` at commit
`89ebd958eeb60aa0892177fe15d55932a5a3d1e8` (branch `dev`, 2026-10-15; latest tag `0.6.0-beta`).
License: MPL-2.0 (`LICENSE.md`); releases before the August 2026 relicensing stay LGPL-3.0
(`RELICENSING.md`). MPL-2.0 is file-level copyleft: code copied from here keeps its MPL header.

Excluded on import: `libs/*.jar` (prebuilt AtumVR jars, Visor's OpenXR library by Phoenix-Ra,
version `1.14.0beta-snapshot#27`). No binaries in this repo.

Status: **reference only.** Not built by CI, not bundled. It is here to compare with our
Vivecraft fork (`perf/VISOR.md`). Do not run it next to Vivecraft; the two mods conflict.

Layout: Stonecutter multi-version build. Shared code is in `visor-core/src/main/java`;
per-version overrides are in `visor-core/mcversion/<version>/`. 1.21.5 Fabric is a supported target.
