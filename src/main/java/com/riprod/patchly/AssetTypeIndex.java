package com.riprod.patchly;

import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.riprod.patchly.core.compile.AssetIndex;
import com.riprod.patchly.core.compile.PatchSource;
import com.riprod.patchly.source.BasePolicy;
import com.riprod.patchly.util.PathUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class AssetTypeIndex implements AssetIndex {
    private record Store(String prefix, String extension) {}

    private final List<AssetPack> packs;
    private final List<PatchSource> sources;
    private final List<Store> stores;
    private final Map<String, Map<String, String>> idIndexByStore = new HashMap<>();

    AssetTypeIndex(@Nonnull List<AssetPack> packs, @Nonnull List<PatchSource> sources) {
        this.packs = packs;
        this.sources = sources;
        this.stores = new ArrayList<>();
        for (AssetStore<?, ?, ?> s : AssetRegistry.getStoreMap().values()) {
            String path = s.getPath();
            if (path == null) continue;
            stores.add(new Store("Server/" + path, s.getExtension()));
        }
        stores.sort((a, b) -> Integer.compare(b.prefix.length(), a.prefix.length()));
    }

    @Nullable
    @Override
    public String resolveRef(@Nonnull String fromTarget, @Nonnull String ref) {
        if (ref.contains("/")) return PathUtil.recoverTargetExtension(ref);
        Store store = codecOf(fromTarget);
        if (store == null) return null;
        Map<String, String> index = idIndexByStore.computeIfAbsent(store.prefix, k -> buildIndex(store));
        return index.get(ref.toLowerCase(Locale.ROOT));
    }

    @Nullable
    private Store codecOf(@Nonnull String target) {
        for (Store s : stores) {
            if (target.startsWith(s.prefix + "/") && target.endsWith(s.extension)) return s;
        }
        return null;
    }

    @Nonnull
    private Map<String, String> buildIndex(@Nonnull Store store) {
        Map<String, String> index = new HashMap<>();
        for (AssetPack p : packs) {
            if (PatchManager.isSyntheticOverridePack(p.getName())) continue;
            Path dir = p.getRoot().resolve(store.prefix);
            if (!Files.isDirectory(dir)) continue;
            try {
                Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String name = file.getFileName().toString();
                        if (name.endsWith(store.extension)) {
                            index.put(stem(name, store.extension), PathUtil.normalizeRelative(p.getRoot(), file));
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException ignored) {
            }
        }
        for (PatchSource s : sources) {
            if (s.kind().basePolicy() != BasePolicy.OPTIONAL) continue;
            String target = s.targetRelative();
            if (target.startsWith(store.prefix + "/") && target.endsWith(store.extension)) {
                int slash = target.lastIndexOf('/');
                index.put(stem(target.substring(slash + 1), store.extension), target);
            }
        }
        return index;
    }

    @Nonnull
    private static String stem(@Nonnull String fileName, @Nonnull String extension) {
        return fileName.substring(0, fileName.length() - extension.length()).toLowerCase(Locale.ROOT);
    }
}
