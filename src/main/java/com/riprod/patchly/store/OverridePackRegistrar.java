package com.riprod.patchly.store;

import com.hypixel.hytale.assetstore.AssetPack.PackSource;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.server.core.asset.AssetModule;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;

public final class OverridePackRegistrar {
    public static final String OVERRIDE_PACK_SUFFIX = "_Patchly";

    private final PluginIdentifier owner;
    private final String packName;
    private final Path dir;
    private final OverrideStore store;

    public OverridePackRegistrar(@Nonnull PluginIdentifier owner, @Nonnull String packName,
            @Nonnull Path dir, @Nonnull OverrideStore store) {
        this.owner = owner;
        this.packName = packName;
        this.dir = dir;
        this.store = store;
    }

    @Nonnull
    public String packName() {
        return packName;
    }

    public boolean isRegistered() {
        return AssetModule.get().getAssetPack(packName) != null;
    }

    public void register() {
        PluginManifest manifest = new PluginManifest(
                owner.getGroup(), owner.getName() + OVERRIDE_PACK_SUFFIX,
                Semver.fromString("1.0.0"),
                "Synthesized merged patches owned by " + owner,
                new ArrayList<>(),
                "",
                null,
                null,
                new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new ArrayList<>(),
                false
        );
        store.writeManifest(owner.getGroup(), owner.getName() + OVERRIDE_PACK_SUFFIX);
        AssetModule.get().registerPack(packName, dir, manifest, PackSource.CLASSPATH);
    }

    public static boolean isSynthetic(@Nonnull String name) {
        if (name.endsWith(OVERRIDE_PACK_SUFFIX)) return true;
        if (name.endsWith(":Hytalor-Overrides")) return true;
        if ("Riprod:patched".equals(name)) return true;
        return false;
    }
}
