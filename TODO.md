# TODO

Open housekeeping items. Remove an item once it is done.

## Delete the outdated remote branches (run on the Mac)

The cloud session's git proxy refuses branch deletes, so this has to run from a local clone. Everything
on these branches is already on `main`, a `release/claudecraft-N` branch or `explore/notes`, except
`ci/probe` (a one-off permissions test) and the vendored Visor source from `perf/visor-compare`
(re-importable from VisorModStudio/Visor @ `89ebd958`).

```
git push origin --delete release/mihail.1 release/mihail.2 release/mihail.3 release/mihail.4 \
archive/vivecraft-1.2.5 archive/vivecraft-1.2.5-main ci/probe \
claudecraft-mixed-reality-exploration claudecraft-unityless-launcher-exploration integration/mihail.1 \
integration/mihail.2 integration/mihail.3 integration/mihail.4 perf/api-register-once perf/gl-error-checks \
perf/immersivemc perf/launcher-bundled-vivecraft perf/launcher-jvm perf/launcher-logs \
perf/launcher-unity-pause perf/lazy-aux-buffers perf/ltw-linear-filter perf/menu-world-free \
perf/menu-world-stream perf/outline-targets perf/settings perf/shared-eye-culling perf/supporter-leak \
perf/tracker-refs perf/visor-compare perf/vivecraft-1.3 perf/worldedit-feasibility perf/xr-input-cache \
perf/xr-late-frame-wait perf/xr-perf-hints perf/xr-refresh-rate perf/xr-session-state perf/xr-startup
git fetch --prune
git branch -r
```

Done when `git branch -r` lists only `origin/main`, `origin/release/claudecraft-1` … `-4` and
`origin/explore/notes`.
