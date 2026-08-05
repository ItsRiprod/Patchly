package com.riprod.patchly.store;

import com.hypixel.hytale.assetstore.AssetPack.PackSource;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.common.semver.SemverRange;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.AssetModule;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;

public final class OverridePackRegistrar {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String OVERRIDE_PACK_SUFFIX = "_Patchly";

    private final PluginIdentifier owner;
    private final String packName;
    private final Path dir;
    private final OverrideStore store;
    private final SemverRange serverVersion;
    private boolean registeredThisSession = false;

    public OverridePackRegistrar(@Nonnull PluginIdentifier owner, @Nonnull String packName,
            @Nonnull Path dir, @Nonnull OverrideStore store, @Nullable SemverRange serverVersion) {
        this.owner = owner;
        this.packName = packName;
        this.dir = dir;
        this.store = store;
        this.serverVersion = serverVersion;
    }

    @Nonnull
    public String packName() {
        return packName;
    }

    public boolean needsRegister() {
        return !registeredThisSession;
    }

    public void register() {
        if (AssetModule.get().getAssetPack(packName) != null) {
            LOGGER.at(Level.WARNING).log(
                    "[patcher] override pack %s was already registered from disk (stale directory from an "
                            + "unclean shutdown); re-registering so it loads last", packName);
        }

        PluginManifest manifest = new PluginManifest(
                owner.getGroup(), owner.getName() + OVERRIDE_PACK_SUFFIX,
                Semver.fromString("1.0.0"),
                "Synthesized merged patches owned by " + owner,
                new ArrayList<>(),
                "",
                null,
                serverVersion,
                new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new ArrayList<>(),
                false
        );
        store.writeManifest(owner.getGroup(), owner.getName() + OVERRIDE_PACK_SUFFIX,
                serverVersion == null ? null : serverVersion.toString());
        AssetModule.get().registerPack(packName, dir, manifest, PackSource.CLASSPATH);
        registeredThisSession = true;
    }

    public static boolean isSynthetic(@Nonnull String name) {
        if (name.endsWith(OVERRIDE_PACK_SUFFIX)) return true;
        if (name.endsWith(":Hytalor-Overrides")) return true;
        if ("Riprod:patched".equals(name)) return true;
        return false;
    }
}
