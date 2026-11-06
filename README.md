# explore/notes: research and exploration write-ups

Notes only, no code; this branch never builds. One directory per topic. Each note was written against
the code of a specific build; paths inside a note refer to that build's tree, not to this branch.
Code and the build history live on `main` and `release/mihail.N` (see `CLAUDE.md` on `main`).

| Topic | Files | Written against | Question |
|---|---|---|---|
| `unityless-launcher/` | `UNITYLESS-LAUNCHER.md`, `LAUNCHER-LEFTOVERS.md` | `release/mihail.4` (2026-10-16) | Can QuestCraft launch without the Unity launcher, and what does the launcher and title screen leave in memory once in game? |
| `mixed-reality/` | `MR-DIORAMA.md` | `release/mihail.4` (2026-10-16) | Is a mixed-reality "diorama" mode cheaper than normal play? Reasoning, unmeasured. |
| `worldedit/` | `WORLDEDIT.md` | `release/mihail.4` (2026-10-16) | Can we run WorldEdit and a god mode on mihail.4? |
| `visor/` | `VISOR.md`, `FINDINGS-section-9.md` | `release/mihail.3` era (2026-10-15) | How does the Visor VR mod compare with our Vivecraft fork, and what can we learn from it (FINDINGS V16)? |

## Where these came from

Migrated on 2026-11-06 from the exploration branches, which were then deleted:

| Old branch | Tip | Notes |
|---|---|---|
| `claudecraft-unityless-launcher-exploration` | `befa38a2` | was `perf/UNITYLESS-LAUNCHER.md`, `perf/LAUNCHER-LEFTOVERS.md` |
| `claudecraft-mixed-reality-exploration` | `bfe87c91` | was `perf/MR-DIORAMA.md` |
| `perf/worldedit-feasibility` | `6beb9510` | was `perf/WORLDEDIT.md` |
| `perf/visor-compare` | `7bee170a` | was `perf/VISOR.md` plus section 9 of `perf/FINDINGS.md`. The branch also vendored Visor itself as `visor-mod/` (VisorModStudio/Visor @ `89ebd958`, dev, 0.6.0-beta, MPL-2.0); it is not kept here. Re-import it from upstream at that commit to follow the `V:` paths in `VISOR.md`. |

## Adding a note

Make a directory named after the topic, put the write-up in it, add a row to the first table (with the
build it was written against) and push to `explore/notes`.
