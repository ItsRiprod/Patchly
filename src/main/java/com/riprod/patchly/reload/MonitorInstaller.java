package com.riprod.patchly.reload;

import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.monitor.AssetMonitor;
import com.riprod.patchly.store.OverridePackRegistrar;
import com.riprod.patchly.watch.PatchChangeListener;
import com.riprod.patchly.watch.PatchMonitorHandler;

import javax.annotation.Nonnull;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

public final class MonitorInstaller {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final PatchChangeListener listener;

    public MonitorInstaller(@Nonnull PatchChangeListener listener) {
        this.listener = listener;
    }

    public boolean install() {
        AssetMonitor monitor = AssetModule.get().getAssetMonitor();
        if (monitor == null) {
            LOGGER.at(Level.INFO).log("[patcher] AssetMonitor unavailable; no hot-reload");
            return false;
        }
        int installed = 0;
        int skipped = 0;
        for (AssetPack pack : AssetModule.get().getAssetPacks()) {
            if (OverridePackRegistrar.isSynthetic(pack.getName())) continue;
            Path serverDir = pack.getRoot().resolve("Server");
            if (!isFolderPack(pack.getRoot()) || !Files.isDirectory(serverDir)) {
                skipped++;
                continue;
            }
            try {
                monitor.monitorDirectoryFiles(serverDir, new PatchMonitorHandler(listener, pack));
                installed++;
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).withCause(e).log("[patcher] failed to monitor pack %s", pack.getName());
            }
        }
        LOGGER.at(Level.INFO).log(
                "[patcher] watching source files in %d folder pack(s); skipped %d jar/zip pack(s)",
                installed, skipped);
        return true;
    }

    private static boolean isFolderPack(@Nonnull Path root) {
        return root.getFileSystem() == FileSystems.getDefault() && Files.isDirectory(root);
    }
}
