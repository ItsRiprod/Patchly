package com.riprod.patchly;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.assetstore.AssetPack.PackSource;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.common.semver.SemverRange;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.AssetPackRegisterEvent;
import com.hypixel.hytale.server.core.asset.AssetPackUnregisterEvent;
import com.hypixel.hytale.server.core.asset.LoadAssetEvent;
import com.hypixel.hytale.server.core.asset.monitor.AssetMonitor;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.PluginManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class PatchManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String PATCHER_VERSION = loadSelfVersion();
    private static final Semver SELF_VERSION = Semver.fromString(PATCHER_VERSION);
    private static final String PROTOCOL_VERSION = "2";

    private static final String SYS_PROP_OWNER = "patcher.owner";
    private static final String SYS_PROP_VERSION = "patcher.version";
    private static final String SYS_PROP_PROTOCOL = "patcher.protocol";
    private static final String SYS_PROP_ACTIVATED = "patcher.activated";

    private static final String OVERRIDE_PACK_SUFFIX = "_PatcherOverrides";
    private static final String META_REQUIRES = "$Requires";
    private static final String META_PRIORITY = "$Priority";
    private static final String META_PREFIX = "$";

    private final JavaPlugin plugin;
    private final PluginIdentifier owner;
    private final String overridePackName;
    private final Path overrideDir;

    private volatile boolean winner = false;
    private boolean legacyDeferred = false;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private final Map<Path, String> patchToTarget = new ConcurrentHashMap<>();
    private volatile boolean monitorInstalled = false;

    public PatchManager(@Nonnull JavaPlugin plugin) {
        this.plugin = plugin;
        this.owner = new PluginIdentifier(plugin.getManifest());
        this.overridePackName = owner.toString() + OVERRIDE_PACK_SUFFIX;
        this.overrideDir = PluginManager.MODS_PATH.resolve(
                owner.getGroup() + "_" + owner.getName() + OVERRIDE_PACK_SUFFIX);
        vote();
    }

    public void install() {
        if (!claimActivation()) return;
        wipeOverrideDirectory();
        plugin.getEventRegistry().register(EventPriority.LAST, LoadAssetEvent.class,
                e -> rebuildAndApply("boot:LoadAssetEvent"));
        plugin.getEventRegistry().register(AssetPackRegisterEvent.class, e -> {
            String name = e.getAssetPack().getName();
            if (isSyntheticOverridePack(name)) return;
            rebuildAndApply("packRegister:" + name);
        });
        plugin.getEventRegistry().register(AssetPackUnregisterEvent.class, e -> {
            String name = e.getAssetPack().getName();
            if (isSyntheticOverridePack(name)) return;
            rebuildAndApply("packUnregister:" + name);
        });
        plugin.getEventRegistry().register(ShutdownEvent.class, e -> shutdown());
    }

    public boolean isActive() {
        return winner;
    }

    public String getOverridePackName() {
        return overridePackName;
    }

    private static String loadSelfVersion() {
        try (InputStream in = PatchManager.class.getResourceAsStream("patchly-version.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String v = props.getProperty("version");
                if (v != null && !v.isBlank()) return v.trim();
            }
        } catch (IOException ignored) {
        }
        return "0.0.0";
    }

    private void vote() {
        String existingOwner = System.getProperty(SYS_PROP_OWNER);
        if (existingOwner != null && !existingOwner.isEmpty()
                && System.getProperty(SYS_PROP_PROTOCOL) == null) {
            legacyDeferred = true;
            return;
        }
        Semver existing = parseVersionProp(System.getProperty(SYS_PROP_VERSION));
        if (existing == null || SELF_VERSION.compareTo(existing) > 0) {
            System.setProperty(SYS_PROP_OWNER, owner.toString());
            System.setProperty(SYS_PROP_VERSION, PATCHER_VERSION);
            System.setProperty(SYS_PROP_PROTOCOL, PROTOCOL_VERSION);
        }
    }

    public boolean claimActivation() {
        if (legacyDeferred) {
            LOGGER.at(Level.WARNING).log(
                    "[patcher] legacy Patchly already active (%s); deferring %s v%s (pre-election behavior)",
                    System.getProperty(SYS_PROP_OWNER), owner, PATCHER_VERSION);
            return false;
        }
        if (System.getProperty(SYS_PROP_ACTIVATED) != null) {
            LOGGER.at(Level.INFO).log(
                    "[patcher] boot election already concluded; deferring %s v%s", owner, PATCHER_VERSION);
            return false;
        }
        if (!isWinner()) {
            LOGGER.at(Level.INFO).log(
                    "[patcher] deferring to %s v%s (this is %s v%s)",
                    System.getProperty(SYS_PROP_OWNER), System.getProperty(SYS_PROP_VERSION),
                    owner, PATCHER_VERSION);
            return false;
        }
        System.setProperty(SYS_PROP_ACTIVATED, owner.toString());
        winner = true;
        LOGGER.at(Level.INFO).log("[patcher] won boot election: %s v%s", owner, PATCHER_VERSION);
        return true;
    }

    private boolean isWinner() {
        return owner.toString().equals(System.getProperty(SYS_PROP_OWNER))
                && PATCHER_VERSION.equals(System.getProperty(SYS_PROP_VERSION));
    }

    @Nullable
    private static Semver parseVersionProp(@Nullable String v) {
        if (v == null || v.isEmpty()) return null;
        try {
            return Semver.fromString(v);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void wipeOverrideDirectory() {
        try {
            if (Files.isDirectory(overrideDir)) {
                Files.walkFileTree(overrideDir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        if (!dir.equals(overrideDir)) Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            Files.createDirectories(overrideDir);
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[patcher] startup wipe failed.");
        }
    }

    public synchronized void rebuildAndApply(@Nonnull String reason) {
        if (!winner) return;

        Map<String, JsonObject> desired = composeDesiredOutputs();
        if (desired.isEmpty()) {
            LOGGER.at(Level.INFO).log("[patcher] no patches resolved (%s)", reason);
            return;
        }

        List<Path> changed = new ArrayList<>();
        for (Map.Entry<String, JsonObject> entry : desired.entrySet()) {
            Path outPath = overrideDir.resolve(entry.getKey());
            String newContent = gson.toJson(entry.getValue());
            if (writeIfChanged(outPath, newContent)) changed.add(outPath);
        }

        if (changed.isEmpty()) {
            LOGGER.at(Level.FINE).log(
                    "[patcher] noop %s - all %d output(s) byte-identical to disk",
                    reason, desired.size());
            return;
        }

        boolean firstRegister = AssetModule.get().getAssetPack(overridePackName) == null;
        if (firstRegister) {
            registerOverridePack();
        }

        reloadChangedFiles(changed);

        if (!monitorInstalled) {
            installMonitorForFolderPacks();
            monitorInstalled = true;
        }
        LOGGER.at(Level.INFO).log(
                "[patcher] %d patch output(s); %d file(s) changed and %s (%s)",
                desired.size(), changed.size(),
                firstRegister ? "registered" : "reloaded", reason);
    }

    public void shutdown() {
        if (!winner) return;
        clearOverrideDirectory(true);
        System.clearProperty(SYS_PROP_OWNER);
        System.clearProperty(SYS_PROP_VERSION);
        System.clearProperty(SYS_PROP_PROTOCOL);
        System.clearProperty(SYS_PROP_ACTIVATED);
    }

    public static boolean isSyntheticOverridePack(@Nonnull String name) {

        if (name.endsWith(OVERRIDE_PACK_SUFFIX)) return true;
        if (name.endsWith(":Hytalor-Overrides")) return true;
        if ("Riprod:patched".equals(name)) return true;
        return false;
    }

    private record PatchEntry(AssetPack pack, int packLoadIndex, Path patchFile) {}

    @Nonnull
    private List<PatchEntry> collectAllPatches() {
        List<PatchEntry> out = new ArrayList<>();
        List<AssetPack> packs = AssetModule.get().getAssetPacks();
        for (int i = 0; i < packs.size(); i++) {
            AssetPack pack = packs.get(i);
            if (isSyntheticOverridePack(pack.getName())) continue;
            final int packIndex = i;
            try {
                Files.walkFileTree(pack.getRoot(), EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                        Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                                if (PathUtil.isPatchFile(file)) {
                                    out.add(new PatchEntry(pack, packIndex, file));
                                }
                                return FileVisitResult.CONTINUE;
                            }
                        });
            } catch (IOException e) {
                LOGGER.at(Level.WARNING).withCause(e).log(
                        "[patcher] walk failed for pack %s", pack.getName());
            }
        }
        return out;
    }

    @Nonnull
    private Map<String, JsonObject> composeDesiredOutputs() {
        record Sortable(PatchEntry entry, int priority, JsonObject patchJson) {}

        List<PatchEntry> entries = collectAllPatches();
        List<Sortable> sortable = new ArrayList<>(entries.size());
        for (PatchEntry e : entries) {
            JsonObject pj = readJson(e.patchFile());
            if (pj == null) continue;
            if (!checkRequires(pj, e.patchFile())) continue;
            int prio = extractPriority(pj);
            stripMetaKeys(pj);
            sortable.add(new Sortable(e, prio, pj));
        }
        sortable.sort(Comparator.comparingInt(Sortable::priority)
                .thenComparingInt(s -> s.entry().packLoadIndex()));

        Map<String, JsonObject> desired = new LinkedHashMap<>();
        Map<Path, String> tracking = new HashMap<>();
        for (Sortable s : sortable) {
            String relPatch = PathUtil.normalizeRelative(s.entry().pack().getRoot(), s.entry().patchFile());
            String relTarget = PathUtil.patchToTargetRelative(relPatch);
            if (relTarget == null) continue;

            JsonObject accumulator = desired.get(relTarget);
            if (accumulator == null) {
                Path basePath = resolveBase(relTarget);
                if (basePath == null) {
                    LOGGER.at(Level.WARNING).log(
                            "[patcher] no base asset for patch %s in %s (looking for %s)",
                            relPatch, s.entry().pack().getName(), relTarget);
                    continue;
                }
                JsonObject base = readJson(basePath);
                if (base == null) continue;
                accumulator = base;
            }
            JsonObject merged = JsonDeepMerge.merge(accumulator, s.patchJson());
            JsonDeepMerge.stripMergeKey(merged);
            desired.put(relTarget, merged);
            tracking.put(s.entry().patchFile(), relTarget);
        }
        patchToTarget.clear();
        patchToTarget.putAll(tracking);
        return desired;
    }

    private boolean writeIfChanged(@Nonnull Path outPath, @Nonnull String newContent) {
        try {
            if (Files.isRegularFile(outPath)) {
                String existing = Files.readString(outPath);
                if (existing.equals(newContent)) return false;
            }
            Files.createDirectories(outPath.getParent());
            Files.writeString(outPath, newContent);
            return true;
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[patcher] write failed: %s", outPath);
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void reloadChangedFiles(@Nonnull List<Path> files) {
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
                    store.loadAssetsFromPaths(overridePackName, List.of(mergedFile),
                            AssetUpdateQuery.DEFAULT, true);
                    reloaded++;
                } catch (Exception e) {
                    LOGGER.at(Level.WARNING).withCause(e).log(
                            "[patcher] reload failed for %s", mergedFile);
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

    private static int extractPriority(@Nonnull JsonObject patch) {
        JsonElement p = patch.get(META_PRIORITY);
        if (p == null || !p.isJsonPrimitive()) return 0;
        JsonPrimitive prim = p.getAsJsonPrimitive();
        return prim.isNumber() ? prim.getAsInt() : 0;
    }

    private boolean checkRequires(@Nonnull JsonObject patch, @Nonnull Path patchFile) {
        JsonElement req = patch.get(META_REQUIRES);
        if (req == null) return true;
        List<String> required = new ArrayList<>();
        if (req.isJsonArray()) {
            JsonArray arr = req.getAsJsonArray();
            for (JsonElement e : arr) {
                if (e.isJsonPrimitive()) required.add(e.getAsString());
            }
        } else if (req.isJsonPrimitive()) {
            required.add(req.getAsString());
        }
        if (required.isEmpty()) return true;

        Map<String, AssetPack> present = new HashMap<>();
        for (AssetPack p : AssetModule.get().getAssetPacks()) present.put(p.getName(), p);

        for (String entry : required) {
            // pack identifiers are exactly Group:Name (one colon); a second colon starts the
            // optional semver range, which itself never contains a colon
            int secondColon = entry.indexOf(':', entry.indexOf(':') + 1);
            String name = secondColon >= 0 ? entry.substring(0, secondColon) : entry;
            String range = secondColon >= 0 ? entry.substring(secondColon + 1) : null;

            AssetPack pack = present.get(name);
            if (pack == null) {
                LOGGER.at(Level.INFO).log("[patcher] skip %s - missing required pack: %s", patchFile, name);
                return false;
            }
            if (range == null) continue;

            Semver version = pack.getManifest().getVersion();
            SemverRange parsedRange;
            try {
                parsedRange = SemverRange.fromString(range);
            } catch (RuntimeException e) {
                LOGGER.at(Level.WARNING).log(
                        "[patcher] %s has malformed version range '%s' for %s; treating as presence-only",
                        patchFile, range, name);
                continue;
            }
            if (!version.satisfies(parsedRange)) {
                LOGGER.at(Level.INFO).log(
                        "[patcher] skip %s - %s v%s does not satisfy required range '%s'",
                        patchFile, name, version, range);
                return false;
            }
        }
        return true;
    }

    private static void stripMetaKeys(@Nonnull JsonObject obj) {
        obj.entrySet().removeIf(e -> e.getKey().startsWith(META_PREFIX));
    }

    @Nullable
    private Path resolveBase(@Nonnull String relativeTarget) {
        Path winning = null;
        for (AssetPack p : AssetModule.get().getAssetPacks()) {
            if (isSyntheticOverridePack(p.getName())) continue;
            Path candidate = p.getRoot().resolve(relativeTarget);
            if (Files.isRegularFile(candidate)) winning = candidate;
        }
        return winning;
    }

    @Nullable
    private JsonObject readJson(@Nonnull Path file) {
        try {
            String content = Files.readString(file);
            try (JsonReader reader = new JsonReader(new StringReader(content))) {
                reader.setStrictness(Strictness.LENIENT);
                return gson.fromJson(reader, JsonObject.class);
            }
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[patcher] failed to parse JSON: %s", file);
            return null;
        }
    }

    private void registerOverridePack() {
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
        AssetModule.get().registerPack(overridePackName, overrideDir, manifest, PackSource.CLASSPATH);
    }

    private void clearOverrideDirectory(boolean includingRoot) {
        if (!Files.isDirectory(overrideDir)) return;
        try {
            Files.walkFileTree(overrideDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (includingRoot || !dir.equals(overrideDir)) Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[patcher] failed to clear override directory");
        }
    }

    private static boolean isFolderPack(@Nonnull Path root) {
        return root.getFileSystem() == FileSystems.getDefault() && Files.isDirectory(root);
    }

    private void installMonitorForFolderPacks() {
        AssetMonitor monitor = AssetModule.get().getAssetMonitor();
        if (monitor == null) {
            LOGGER.at(Level.INFO).log("[patcher] AssetMonitor unavailable; no hot-reload");
            return;
        }
        int installed = 0;
        int skipped = 0;
        for (AssetPack pack : AssetModule.get().getAssetPacks()) {
            if (isSyntheticOverridePack(pack.getName())) continue;
            Path serverDir = pack.getRoot().resolve("Server");
            if (!isFolderPack(pack.getRoot()) || !Files.isDirectory(serverDir)) {
                skipped++;
                continue;
            }
            try {
                monitor.monitorDirectoryFiles(serverDir, new PatchMonitorHandler(this, pack));
                installed++;
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).withCause(e).log(
                        "[patcher] failed to monitor pack %s", pack.getName());
            }
        }
        LOGGER.at(Level.INFO).log(
                "[patcher] watching .patch in %d folder pack(s); skipped %d jar/zip pack(s)",
                installed, skipped);
    }

    void onPatchEvent(@Nonnull AssetPack pack, @Nonnull Path patchFile) {
        if (!winner) return;
        if (!PathUtil.isPatchFile(patchFile)) return;
        if (Files.exists(patchFile)) {
            rebuildAndApply("patchEdit:" + pack.getName() + ":" + patchFile.getFileName());
        } else {
            String target = patchToTarget.remove(patchFile);
            if (target != null) {
                try {
                    Files.deleteIfExists(overrideDir.resolve(target));
                } catch (IOException e) {
                    LOGGER.at(Level.WARNING).withCause(e).log(
                            "[patcher] failed to delete override %s", target);
                }
            }
            rebuildAndApply("patchDelete:" + pack.getName() + ":" + patchFile.getFileName());
        }
    }

    void onBaseEvent(@Nonnull AssetPack pack, @Nonnull Path changedJson) {
        if (!active) return;
        rebuildAndApply("baseEdit:" + pack.getName() + ":" + changedJson.getFileName());
    }
}
