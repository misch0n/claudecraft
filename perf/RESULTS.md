# perf/RESULTS.md

Measurement log. Protocol and rules: `CLAUDE.md` ("Measurement protocol"). No claim of a win
without an entry here. "Builds" and "runs" are not results.

Conventions (keep them identical across entries so runs stay comparable):
- Memory in MB, from `adb shell dumpsys meminfo com.qcxr.qcxr` (`tools/mem.sh <label>`).
  State in the entry which TOTAL line was used (PSS or RSS) and keep using it.
- Frame times from OVR Metrics Tool CSV captures (`tools/metrics.sh`).
- Fixed test world, render distance 6, simulation distance minimum, same spawn spot.
- Raw data lives in `perf/runs/<timestamp>/` (git-ignored; the repo is public). Name the folder
  in each entry.

## Comparisons (before / after, one block per task)

Filled in at each checkpoint. A null or negative result goes here too.

| Task | Metric | Before (run) | After (run) | Δ | Verdict |
|---|---|---|---|---|---|

## Runs

One entry per measured run, oldest first. Copy the template.

### R0 — CI desktop smoke, all fixes off vs on (2026-10-13)

**Not a headset result.** GitHub Actions `ubuntu-latest`, Xvfb with Mesa software GL, the `NULLVR`
provider, a vanilla flat world, 60 s after joining. Software GL keeps "GPU" buffers in process RAM
(`RssAnon`), loosely like the Quest's shared memory; ZGC's heap shows up as `RssShmem`. It shows that
the fixes run without crashes or mixin errors and the direction of the memory change. It is not
evidence for the headset (CLAUDE.md rule 6).

- **Build tag:** `1.2.5-mihail.1` (commit `65bec23`, branch `integration/mihail.1`, Actions run `38426917673`)
- **Toggles:** fixes-off = every perf toggle off (`glErrorChecks` on, `xrRefreshRate MAX`); fixes-on = PLAN defaults
- **JVM:** both ZGC, `-Xmx2048m`. Off: `-Xms1024m`. On: `-Xms512m -XX:SoftMaxHeapSize=1536m -XX:ZUncommitDelay=30`
  (the claudecraft launcher policy). So the "on" column includes the heap policy, not only Vivecraft changes.

| `[VCPERF] mem` label | Off: heap used / committed | Off: VmRSS | On: heap used / committed | On: VmRSS |
|---|---|---|---|---|
| vr_init | 1294 / 2048 | 3083 | 1340 / 2048 | 2753 |
| join | 1776 / 2048 | 3194 | 1518 / 2048 | 2942 |
| join+60s | 1908 / 2048 | 3449 (anon 1470, shmem 1876) | 1190 / 1432 | 2527 (anon 1020, shmem 1403) |
| peak RSS / PSS (2 s samples) | | 3451 / 3410 | | 3013 / 2972 |

- **Noise:** one run each. An earlier fixes-off run (run `38423698961`, commit `531ce9c`, same Vivecraft
  code) ended at join+60s with VmRSS 3655, so run-to-run spread is at least ~200 MB.
- **Crashed:** no (both).
- **Notes:** at join+60s, ZGC had returned 616 MB of committed heap with the claudecraft policy
  (2048 → 1432 committed). The other ~450 MB of the RSS gap is non-heap (`RssAnon`). Errors logged in
  both runs, none from a perf toggle: the Vivecraft update check cannot parse our `mihail.N` version
  suffix (background thread, harmless); upstream's `vivecraft:core/blit_vr` shader file is missing (used
  only by the desktop mirror, which QuestCraft turns off); one `ModelViewStack was empty!`.

### R1 — CI desktop smoke, mihail.3: fixes off / on / on + ImmersiveMC (2026-10-15)

**Not a headset result.** Same harness as R0 (GitHub Actions, Xvfb + Mesa software GL, `NULLVR`, vanilla
flat world, 60 s after joining), now with Sodium 0.6.13 really loaded. One run per variant.

- **Build tag:** `1.3.15-mihail.3` (commit `6aef7234`, branch `integration/mihail.3`, Actions run `38765161759`)
- **Variants:** fixes-off = every perf toggle off; fixes-on = PLAN defaults (all on, incl. `sharedEyeCulling`,
  `xrPerfHints`); fixes-on-immersivemc = fixes-on plus ImmersiveMC `1.6.0-beta1+claudecraft.3` in `mods/`.
- **JVM:** as R0 (off: ZGC `-Xms1024m -Xmx2048m`; on: the claudecraft policy, `-Xms512m`, SoftMax 1536 MB).

| `[VCPERF] mem` label | Off: heap used / committed, VmRSS | On | On + ImmersiveMC |
|---|---|---|---|
| vr_init | 1350 / 2048, 3025 | 1388 / 2048, 2886 | 1814 / 2048, 2792 |
| join | 1644 / 2048, 3188 | 1762 / 2028, 3016 | 2016 / 2048, 3133 |
| join+60s | 1696 / 2048, 3350 | 1198 / 1434, 2648 | 1386 / 1434, 2691 |
| peak RSS / PSS (2 s samples) | 3350 / 3309 | 3145 / 3104 | 3203 / 3163 |

**`[VCPERF] cull`, second 30 s window (steady state), Sodium `setupTerrain` time per eye pass:**

| | First eye | Second eye | Frames shared / per eye | Fallbacks |
|---|---|---|---|---|
| Off | 0.197 ms × 260 | 0.217 ms × 260 | 0 / 0 (feature off) | – |
| On | 0.190 ms × 283 | skipped (283 of 283) | 283 / 0 | none |
| On + ImmersiveMC | 0.201 ms × 389 | skipped (389 of 389) | 389 / 0 | none |

- **Shared eye culling worked in CI:** the Sodium hook matched 0.6.13 (`sodiumHook=true`), every frame was
  shared, nothing fell back, and the apex sat 3.4 cm behind the eyes. The right eye's culling work
  (~0.2 ms per frame here) disappeared. A flat world at desktop speed is the smallest possible case;
  the headset's CPU and real terrain are where it should matter.
- **Memory:** fixes-on ends 700 MB lower at join+60s than fixes-off (same pattern as R0: ZGC returns
  ~600 MB of committed heap). ImmersiveMC adds ~40 MB RSS at join+60s, within the ~200 MB run-to-run noise.
- **Crashed:** no (all three).
- **Found and fixed on `perf/api-register-once` (not in this build):** the ImmersiveMC run logs
  `IllegalArgumentException: InteractModule 'immersivemc:bag_open' is already added`. Vivecraft runs its add-on
  registration a second time during the first resource load. ImmersiveMC's second registration throws, and
  Minecraft treats that as a failed initial load: "Caught error loading resourcepacks, removing all selected
  resourcepacks", then a full reload. With the new toggle `apiRegisterOnce` (run `38771530766`, commit
  `41313c31`), the second call is skipped and logged (`[VCPERF] api registration requested again`). The
  exception and the rollback are gone, and that smoke job took ~4 min instead of ~10. On the headset,
  mihail.3 without it would deselect resource packs and reload everything at each launch.
- Other errors as in R0 (update-check version parse, missing upstream `blit_vr` shader, `ModelViewStack
  was empty!`).

<!-- TEMPLATE

### R<N> — <short label> (<YYYY-MM-DD>)

- **Build tag:** `1.2.5-mihail.N` (commit `<sha>`, branch `<branch>`)
- **`[VCPERF]` line:** `<paste from latest.log>`
- **Toggles vs baseline:** <none | list>
- **Raw data:** `perf/runs/<timestamp>/`
- **TOTAL used:** PSS | RSS

| Snapshot | TOTAL | Native Heap | Graphics | `[VCPERF]` heap used / committed | `[VCPERF]` VmRSS |
|---|---|---|---|---|---|
| launcher (Unity only) | | | | – | – |
| title | | | | | |
| load (~10 s) | | | | | |
| settled (60 s) | | | | | |
| walk (2 min) | | | | | |
| long (20–30 min) | | | | | |

| Frame time median (ms) | Frame time p95 (ms) | Dropped frames | Other (FPS, CPU/GPU level, thermals) |
|---|---|---|---|
| | | | |

- **Crashed:** no | yes (<report/log excerpt>)
- **Notes:**

-->
