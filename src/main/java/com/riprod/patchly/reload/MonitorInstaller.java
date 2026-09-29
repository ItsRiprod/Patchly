package com.riprod.patchly.reload;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.riprod.patchly.PatchManager;
import com.riprod.patchly.store.OverridePackRegistrar;
import com.riprod.patchly.watch.PatchMonitorHandler;

import javax.annotation.Nonnull;

public final class MonitorInstaller {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final PatchManager listener;

    public MonitorInstaller(@Nonnull PatchManager listener) {
        this.listener = listener;
    }

    public boolean install() {
        var monitor = AssetModule.get().getAssetMonitor();
        if (monitor == null) {
            LOGGER.atInfo().log("[patcher] AssetMonitor unavailable; no hot-reload");
            return false;
        }
        int installed = 0;
        int skipped = 0;
        for (var pack : AssetModule.get().getAssetPacks()) {
            if (OverridePackRegistrar.isSynthetic(pack.getName())) continue;
            var serverDir = pack.getRoot().resolve("Server");
            if (pack.isImmutable()) {
                skipped++;
                continue;
            }
            try {
                monitor.monitorDirectoryFiles(serverDir, new PatchMonitorHandler(listener, pack));
                installed++;
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("[patcher] failed to monitor pack %s", pack.getName());
            }
        }
        LOGGER.atInfo().log(
                "[patcher] watching source files in %d folder pack(s); skipped %d jar/zip pack(s)",
                installed, skipped);
        return true;
    }
}
