package com.riprod.patchly.reload;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;

public final class AssetReloader {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Path overrideDir;
    private final String packName;

    public AssetReloader(@Nonnull Path overrideDir, @Nonnull String packName) {
        this.overrideDir = overrideDir;
        this.packName = packName;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void reload(@Nonnull List<Path> files) {
        Path serverRoot = overrideDir.resolve("Server");
        if (!Files.isDirectory(serverRoot)) return;
        int reloaded = 0;
        int skippedParent = 0;
        for (Path mergedFile : files) {
            for (AssetStore store : AssetRegistry.getStoreMap().values()) {
                String storePath = store.getPath();
                if (storePath == null) continue;
                Path storeDir = serverRoot.resolve(storePath);
                if (!mergedFile.startsWith(storeDir)) continue;
                if (!mergedFile.getFileName().toString().endsWith(store.getExtension())) continue;
                Object key = store.decodeFilePathKey(mergedFile);
                if (key != null && hasChildrenInStore(store, key)) {
                    skippedParent++;
                    break;
                }
                try {
                    store.loadAssetsFromPaths(packName, List.of(mergedFile), AssetUpdateQuery.DEFAULT, true);
                    reloaded++;
                } catch (Exception e) {
                    LOGGER.at(Level.WARNING).withCause(e).log("[patcher] reload failed for %s", mergedFile);
                }
                break;
            }
        }
        LOGGER.at(Level.FINE).log(
                "[patcher] reloaded %d changed file(s); skipped %d parent(s) to avoid re-pollution",
                reloaded, skippedParent);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean hasChildrenInStore(AssetStore store, Object key) {
        try {
            java.util.Set children = store.getAssetMap().getChildren(key);
            return children != null && !children.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
