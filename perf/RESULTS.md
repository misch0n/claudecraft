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

<!-- TEMPLATE

### R<N> — <short label> (<YYYY-MM-DD>)

- **Build tag:** `1.3.4-mihail.N` (commit `<sha>`, branch `<branch>`)
- **`[VCPERF]` line:** `<paste from latest.log>`
- **Toggles vs baseline:** <none | list>
- **Raw data:** `perf/runs/<timestamp>/`
- **TOTAL used:** PSS | RSS

| Snapshot | TOTAL | Java Heap | Native Heap | Graphics |
|---|---|---|---|---|
| title | | | | |
| load (~10 s) | | | | |
| settled (60 s) | | | | |
| walk (2 min) | | | | |

| Frame time median (ms) | Frame time p95 (ms) | Dropped frames | Other (FPS, CPU/GPU level, thermals) |
|---|---|---|---|
| | | | |

- **Crashed:** no | yes (<report/log excerpt>)
- **Notes:**

-->
