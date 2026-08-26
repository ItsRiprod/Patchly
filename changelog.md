# v3.5.4

Updated patchly to correctly be MIT

Updated patchly to support Update 6
Note: it will continue to error until update 6 fully releases due to semver issues

# v3.5.3

- `$Import` now works inside an array element, as the guide always said it did. Every array path stripped all `$`-prefixed keys off an element before merging it, so the marker was gone before it could fire. Element payloads now keep registered object markers, the import scopes to the array it sits in, and the matching element is selected from the imported asset using the same `$Match` you already wrote. It applies on an append too, so `$Import` with `+` pulls a template for a brand-new entry. If the imported asset has no matching element, nothing is imported and your own keys still apply.

- Source hot-reload no longer dies permanently when the asset monitor is not ready. The installer reported failure the same way as success, and the caller latched its one-shot flag regardless, so a single early miss disabled source watching for the rest of the process. It now retries on the next rebuild.

# v3.5.2

- Patches now bind to assets the way the engine does. Sources are grouped by asset identity (the store plus the filename stem) instead of by their literal folder path, so a `.patch` or `.put` may sit in any subfolder under its store root and still land on the asset it names. Previously the compiler treated `Armor/X.patch` and `Armor/Test/X.patch` as two unrelated targets even though the engine sees one asset.

- One asset now produces exactly one override file, written at the upstream asset's own path. Before this, sources in different folders each emitted their own file, both claiming the same asset in the same pack, and whichever the engine scanned last silently won. Existing override output relocates to the upstream mirror path on first boot. The override directory is rebuilt from scratch at startup, so nothing stale is left behind.

- A `.put` now satisfies a sibling `.patch` no matter which the filesystem walk reached first. Only `.batch` could read a `.put` seed before, so a `.patch` walked ahead of the `.put` creating its base was dropped with a misleading "no base asset" warning.

- Merge order among sources that tie on `$Priority` is deterministic instead of filesystem directory order. Ties break on kind, then path depth, then path. A `.put` merges first because it creates the asset, then `.batch` bodies as folder defaults, then `.patch` files, with deeper paths applied last. This matters most for `$Import`, which is a patch payload rather than a base lookup. A `.put` importing a whole upstream asset overwrites every field written before it, so an equal-priority sibling patch could win on one machine and lose on another.

- `/patchly explain <target>` now lists every source that built an asset under a single entry, including sources from other folders, and no longer reports a false ambiguity when two folders name the same asset.

# v3.5.1
Dropped Java version from 26 to 25 for compatibility

# v3.5.0

Added `.batch`, a folder-scoped patch whose reserved keys gate every source at or below its directory and whose body merges once into every target those sources produce.

Added `/patchly explain <target>`, which lists every source that built one asset in merge order.

Gated-source log lines moved from INFO to FINE.