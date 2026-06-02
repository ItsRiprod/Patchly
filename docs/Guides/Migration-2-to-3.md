---
title: "Upgrading 2.x to 3.x"
order: 3
published: true
draft: false
---
# Upgrading 2.x to 3.x

Patchly 3.0 changes two things you can see: the `PatchManager` integration shrinks from four touch points to two, and multiple installed copies now elect the **newest** version as the single active owner instead of the first one to load. Pack authors gain optional version ranges in `$Requires`.

This guide covers what changed and exactly what to edit. If you only ship `.patch` files (no Java), skip to [For pack developers](#for-pack-developers-version-ranges-in-requires) - the only change that affects you is additive.

## At a glance

| Audience | What changed | Action needed |
|---|---|---|
| Mod developers (shading Patchly) | `PatchManager` API: construct from the plugin, drive with one `install()` call | Edit your `JavaPlugin`, bump the `deps/` jar |
| Pack developers (`.patch` only) | `$Requires` accepts an optional semver range | None; ranges are opt-in |
| Anyone running multiple copies | Election is highest-version-wins, decided once at boot | Remove stale standalone jars (recommended) |

---

## For mod developers: the new `PatchManager` API

Patchly now takes your plugin and wires its own lifecycle. You construct it (so it can vote in the version election before any asset loads), then call `install()` once in `setup()`. Patchly registers the asset events, the shutdown hook, and the directory wipe itself.

### Before (2.x) - four touch points

```java
public final class MyPlugin extends JavaPlugin {
    private final PatchManager patchManager;

    public MyPlugin(JavaPluginInit init) {
        super(init);
        patchManager = new PatchManager(this.getManifest());
    }

    @Override
    public CompletableFuture<Void> preLoad() {
        patchManager.preLoad();
        return super.preLoad();
    }

    @Override
    protected void setup() {
        getEventRegistry().register(EventPriority.LAST, LoadAssetEvent.class,
                e -> patchManager.rebuildAndApply("boot:LoadAssetEvent"));
        getEventRegistry().register(AssetPackRegisterEvent.class, e -> {
            String name = e.getAssetPack().getName();
            if (PatchManager.isSyntheticOverridePack(name)) return;
            patchManager.rebuildAndApply("packRegister:" + name);
        });
        getEventRegistry().register(AssetPackUnregisterEvent.class, e -> {
            String name = e.getAssetPack().getName();
            if (PatchManager.isSyntheticOverridePack(name)) return;
            patchManager.rebuildAndApply("packUnregister:" + name);
        });
    }

    @Override
    protected void shutdown() {
        patchManager.shutdown();
    }
}
```

### After (3.x) - two touch points

```java
import com.riprod.patchly.PatchManager;

public final class MyPlugin extends JavaPlugin {
    private final PatchManager patchManager;

    public MyPlugin(JavaPluginInit init) {
        super(init);
        patchManager = new PatchManager(this);   // pass the plugin, not the manifest
    }

    @Override
    protected void setup() {
        patchManager.install();                  // claims activation and wires everything
    }
}
```

You can delete the `preLoad` and `shutdown` overrides if Patchly was their only user, along with the `LoadAssetEvent`, `AssetPackRegisterEvent`, and `AssetPackUnregisterEvent` imports.

> If your plugin needs its own `LoadAssetEvent` handler for unrelated work, keep it. `install()` registers a separate handler; multiple handlers on the same event coexist. Just drop the `patchManager.rebuildAndApply(...)` line from yours.

### What moved where

| 2.x call | 3.x equivalent |
|---|---|
| `new PatchManager(getManifest())` | `new PatchManager(this)` |
| `preLoad()` | folded into `install()` (winner wipes its own override dir) |
| three `getEventRegistry().register(...)` calls | folded into `install()` |
| `shutdown()` | folded into `install()` (registers a `ShutdownEvent` handler) |

`isActive()`, `getOverridePackName()`, `rebuildAndApply(reason)`, and `isSyntheticOverridePack(name)` are unchanged and still public. If you had custom wiring, you can still call `rebuildAndApply` yourself; `install()` is the convenience path.

### Bump the shaded jar

Update the file you shade in `build.gradle.kts`:

```kotlin
val shaded by configurations.creating

dependencies {
    shaded(files("deps/Patchly-3.0.0.jar"))
    implementation(files("deps/Patchly-3.0.0.jar"))
}
```

The Shadow setup is otherwise unchanged. See [For Mod Developers](Mod-Developers) for the full build block.

---

## For pack developers: version ranges in `$Requires`

`$Requires` still gates a patch on the presence of named packs, and the bare `Group:Name` form is unchanged. You can now append an **optional** semver range after the pack name to also gate on its version:

```json
{ "$Requires": "Riprod:Hexcode:>=0.5.0", "Armor": { } }
```

The range is everything after the pack's `Group:Name` (the third colon-separated segment onward). It accepts the full range syntax - `>=`, `>`, `<=`, `<`, `=`, `^`, `~`, hyphen ranges, and `||` alternatives:

```json
{ "$Requires": ["Riprod:Hexcode:>=0.5.0 <1.0.0", "Author:Other:^2.0.0"] }
```

A patch is skipped (with a log line) if a required pack is missing **or** present at a version the range rejects. Omit the range and Patchly only checks presence, exactly as before. A malformed range logs a warning and falls back to presence-only, so a typo never silently drops your patch.

---

## Behavioral change: highest version wins

In 2.x the first `PatchManager` to construct claimed the JVM-wide owner property and kept it; a newer copy loading later stayed disabled. A standalone jar in `mods/` would beat a newer copy shaded into a plugin simply by loading first.

In 3.x every copy votes its own version at construction, and the **highest version** becomes the single active owner. The decision is made once, at boot, and is final - a copy hot-loaded after boot is ignored even if it is newer. Losing copies register nothing and do no work.

This needs no action. It is the reason for the upgrade: ship a newer Patchly bundled in your plugin and it wins over an older standalone jar automatically.

> "Newest wins" compares the Patchly version, which 3.x reads from a build-stamped resource inside its own classes - so a shaded copy reports its real Patchly version, not the host plugin's. Keep your `deps/` jar current to stay the winner.

---

## Running a 2.x copy alongside 3.x

The version election only works between copies that both understand it. A 2.x copy uses the old first-claim-wins logic and cannot be told to step aside after it has claimed.

- **3.x loads first:** the 2.x copy sees the slot taken and defers. 3.x wins. Clean.
- **2.x loads first:** 3.x detects the legacy claim, logs a warning, and defers to it - the server behaves exactly as it did on 2.x. The 2.x copy does the patching.

Either way the server runs and patches apply. To guarantee the 3.x behavior, make sure every installed copy is 3.x: upgrade or remove any standalone `Patchly-2.x.jar` in `mods/` once your plugins bundle 3.x.

---

## Next steps

- **[For Mod Developers](Mod-Developers)** - the full 3.x integration and build setup.
- **[For Pack Developers](Pack-Developers)** - shipping `.patch` files and requiring Patchly.
