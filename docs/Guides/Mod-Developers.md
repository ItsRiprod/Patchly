---
title: "For Mod Developers"
order: 2
published: true
draft: false
---
# For Mod Developers

This is for **Java plugins** that want their own patching built in, so your mod ships `.patch` files and applies them itself with no separate `Patchly.jar` for the user to install. You bundle Patchly's classes into your plugin jar with the Gradle Shadow plugin and drive `PatchManager` from your plugin lifecycle.

> Asset-only pack and just want to ship `.patch` files? You do not need any of this. See **[For Pack Developers](Pack-Developers)**.

## Quickstart

1. Put `Patchly-X.Y.Z.jar` in a `deps/` folder and shade it via a dedicated `shaded` configuration (so only Patchly is folded in).
2. Construct a `PatchManager(this)` in your plugin.
3. Call `install()` once in `setup()`.
4. Ship your `.patch` files the same way a pack developer does.

The two code blocks below are everything you need to copy. The explanations and caveats come after, skippable until you hit them.

## 1. Add the Shadow plugin and the lib jar

Drop `Patchly-X.Y.Z.jar` into a `deps/` folder in your project, then wire up Shadow:

```kotlin
plugins {
    id("hytale-mod") version "0.+"
    id("com.gradleup.shadow") version "8.3.5"
}

// a dedicated configuration so ONLY Patchly is shaded, not your compile deps
val shaded by configurations.creating

dependencies {
    // compile against the API, and mark it for shading into the final jar
    shaded(files("deps/Patchly-3.0.0.jar"))
    implementation(files("deps/Patchly-3.0.0.jar"))
}

tasks.shadowJar {
    archiveClassifier.set("")        // shadow jar IS the published artifact
    mergeServiceFiles()
    configurations = listOf(shaded)  // shade only what's in `shaded`
}

tasks.jar { enabled = false }        // disable the thin jar
tasks.build { dependsOn(tasks.shadowJar) }
```

## 2. Drive `PatchManager` from your plugin

`PatchManager` needs two touch points in your `JavaPlugin`: construct it with your plugin, then call `install()` once in `setup()`. Patchly wires its own asset events, shutdown hook, and override-directory wipe:

```java
import com.riprod.patchly.PatchManager;

public final class MyPlugin extends JavaPlugin {
    private final PatchManager patchManager;

    public MyPlugin(JavaPluginInit init) {
        super(init);
        patchManager = new PatchManager(this);   // votes in the version election
    }

    @Override
    protected void setup() {
        patchManager.install();                  // claims activation and wires everything
    }
}
```

## 3. Ship your `.patch` files

Put `.patch` files in your pack tree exactly as a pack developer would: mirror the target asset's path with a `.patch` extension. See **[For Pack Developers](Pack-Developers)** and the [syntax reference](../) for the merge rules (`+` append, `null` removal, `$Requires`, `$Priority`).

That is the working setup. The sections below explain why each piece is shaped this way.

---

## Why bundle instead of depend?

Patchly is a single self-contained class set with zero runtime dependencies. Shading it into your jar means:

- Your users install **one** jar, not two.
- Your patches apply even if no standalone `Patchly.jar` is present.
- If a standalone jar or another bundling mod is also installed, Patchly's JVM-wide version election ensures exactly **one** instance - the **newest version** - does the work. Older copies defer and noop. No duplicate merges, no conflicts. Keep your `deps/` jar current to stay the winner.

## Why the dedicated `shaded` configuration?

The `shaded` configuration is the important detail. Without `configurations = listOf(shaded)`, Shadow would try to fold in every dependency, including `compileOnly` server libs. Scoping it to a one-jar configuration keeps the output to your code plus Patchly.

## What each `PatchManager` call does

| Call | When | Purpose |
|---|---|---|
| `new PatchManager(this)` | construction | Votes this copy's version into the JVM-wide election. The plugin's manifest names the override pack. |
| `install()` | in `setup()` | If this copy won the election, wipes the override directory and registers everything below; otherwise returns and does nothing. |

`install()` wires these internally, so you never write them yourself:

| Wired by `install()` | When | Purpose |
|---|---|---|
| `rebuildAndApply` on `LoadAssetEvent` (priority `LAST`) | after all base assets load | The initial merge pass. `LAST` ensures every base asset is resolved first. |
| `rebuildAndApply` on pack register/unregister | runtime pack changes | Re-merges so patches still apply to packs added or removed after boot. A built-in guard skips Patchly's own synthetic pack to avoid an infinite loop. |
| directory wipe + `ShutdownEvent` cleanup | activation and teardown | Clean slate on win; clears the override directory and releases the election on shutdown. |

## Caveat: testing with `./gradlew runServer`

**Do not** also place a standalone `Patchly.jar` in the dev server's `mods/` folder while testing a bundling mod. The dev server scans your `deps/` jar, finds its `manifest.json`, and registers it as a pack. If a second copy is in `mods/` it registers twice and the server dies. You do not need the standalone jar anyway: your shaded mod already contains Patchly. Just remove it from `mods/`.

## Coexistence

In production, your bundling mod, a standalone `Patchly.jar`, and other bundling mods can all be installed at once. Each copy votes its version at construction, and the **newest version** becomes the single active owner; every other instance logs that it is deferring and does nothing. The decision is made once at boot and is final. Your mod works standalone, alongside the standalone jar, and alongside other Patchly-bundling mods. No coordination required.

> A Patchly **2.x** copy predates the version election and uses the old first-claim-wins logic. If a 2.x copy loads first, 3.x defers to it and the server behaves as it did on 2.x; if 3.x loads first, the 2.x copy defers. Either way one instance does the work. To guarantee newest-wins everywhere, keep every installed copy on 3.x.
