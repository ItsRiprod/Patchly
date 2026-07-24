package com.riprod.patchly;

import com.hypixel.hytale.assetstore.AssetMap;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.riprod.patchly.core.compile.AssetIndex;
import com.riprod.patchly.core.compile.PatchSource;
import com.riprod.patchly.source.BasePolicy;
import com.riprod.patchly.util.PathUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class AssetTypeIndex implements AssetIndex {
    private final List<PatchSource> sources;

    AssetTypeIndex(@Nonnull List<PatchSource> sources) {
        this.sources = sources;
    }

    @Nullable
    @Override
    public String resolveRef(@Nonnull String fromTarget, @Nonnull String ref) {
        if (ref.contains("/")) return PathUtil.recoverTargetExtension(ref);
        AssetStore<?, ?, ?> store = storeFor(fromTarget);
        if (store == null) return null;
        String existing = existingTarget(store, ref);
        return existing != null ? existing : pendingPutTarget(store, ref);
    }

    @Nullable
    Path upstreamBasePath(@Nonnull String target) {
        AssetStore<?, ?, ?> store = storeFor(target);
        Path identity = store == null ? null : upstreamPath(store, idOf(target, store.getExtension()));
        return identity != null ? identity : mirrorBasePath(target);
    }

    @Nullable
    private static Path mirrorBasePath(@Nonnull String target) {
        Path best = null;
        for (AssetPack p : AssetModule.get().getAssetPacks()) {
            if (PatchManager.isSyntheticOverridePack(p.getName())) continue;
            Path candidate = p.getRoot().resolve(target);
            if (Files.isRegularFile(candidate)) best = candidate;
        }
        return best;
    }

    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String existingTarget(@Nonnull AssetStore store, @Nonnull String id) {
        Object key = store.decodeStringKey(id);
        if (key == null) return null;
        Path p = store.getAssetMap().getPath(key);
        return p == null ? null : serverRelative(p);
    }

    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Path upstreamPath(@Nonnull AssetStore store, @Nonnull String id) {
        Object key = store.decodeStringKey(id);
        if (key == null) return null;
        AssetMap map = store.getAssetMap();
        String top = map.getAssetPack(key);
        if (top == null) return null;
        if (!PatchManager.isSyntheticOverridePack(top)) return map.getPath(key);
        Path best = null;
        for (AssetPack p : AssetModule.get().getAssetPacks()) {
            if (PatchManager.isSyntheticOverridePack(p.getName())) continue;
            Object path = map.getPathMap(p.getName()).get(key);
            if (path != null) best = (Path) path;
        }
        return best;
    }

    @Nullable
    private String pendingPutTarget(@Nonnull AssetStore<?, ?, ?> store, @Nonnull String id) {
        String prefix = "Server/" + store.getPath();
        String ext = store.getExtension();
        for (PatchSource s : sources) {
            if (s.kind().basePolicy() != BasePolicy.OPTIONAL) continue;
            String t = s.targetRelative();
            if (t.startsWith(prefix + "/") && t.endsWith(ext) && idOf(t, ext).equals(id)) return t;
        }
        return null;
    }

    @Nullable
    private static AssetStore<?, ?, ?> storeFor(@Nonnull String target) {
        AssetStore<?, ?, ?> best = null;
        int bestLen = -1;
        for (AssetStore<?, ?, ?> s : AssetRegistry.getStoreMap().values()) {
            String path = s.getPath();
            if (path == null) continue;
            String prefix = "Server/" + path;
            if (prefix.length() > bestLen && target.startsWith(prefix + "/") && target.endsWith(s.getExtension())) {
                best = s;
                bestLen = prefix.length();
            }
        }
        return best;
    }

    @Nonnull
    private static String idOf(@Nonnull String target, @Nonnull String extension) {
        int slash = target.lastIndexOf('/');
        String fileName = slash < 0 ? target : target.substring(slash + 1);
        return fileName.endsWith(extension) ? fileName.substring(0, fileName.length() - extension.length()) : fileName;
    }

    @Nullable
    private static String serverRelative(@Nonnull Path p) {
        for (int i = p.getNameCount() - 1; i >= 0; i--) {
            if (p.getName(i).toString().equals("Server")) {
                return p.subpath(i, p.getNameCount()).toString().replace('\\', '/');
            }
        }
        return null;
    }
}
