package com.riprod.patchly.watch;

import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.server.core.asset.monitor.AssetMonitorHandler;
import com.hypixel.hytale.server.core.asset.monitor.EventKind;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.Map;

public final class PatchMonitorHandler implements AssetMonitorHandler {
    private final PatchChangeListener listener;
    private final AssetPack pack;
    private final String key;

    public PatchMonitorHandler(@Nonnull PatchChangeListener listener, @Nonnull AssetPack pack) {
        this.listener = listener;
        this.pack = pack;
        this.key = "PatchMonitor:" + pack.getName();
    }

    @Override
    public Object getKey() {
        return key;
    }

    @Override
    public boolean test(Path path, EventKind eventKind) {
        return listener.isSourceFile(path) || listener.isBaseFile(path);
    }

    @Override
    public void accept(Map<Path, EventKind> events) {
        for (Map.Entry<Path, EventKind> e : events.entrySet()) {
            Path path = e.getKey();
            if (listener.isSourceFile(path)) {
                listener.onPatchEvent(pack, path);
            } else if (listener.isBaseFile(path)) {
                listener.onBaseEvent(pack, path);
            }
        }
    }
}
