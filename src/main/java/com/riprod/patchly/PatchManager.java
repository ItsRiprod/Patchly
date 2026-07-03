package com.riprod.patchly;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.semver.Semver;
import com.hypixel.hytale.common.semver.SemverRange;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.AssetPackRegisterEvent;
import com.hypixel.hytale.server.core.asset.AssetPackUnregisterEvent;
import com.hypixel.hytale.server.core.asset.LoadAssetEvent;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.riprod.patchly.core.JsonDeepMerge;
import com.riprod.patchly.core.compile.CompileResult;
import com.riprod.patchly.core.compile.PatchCompiler;
import com.riprod.patchly.core.compile.PatchSource;
import com.riprod.patchly.core.directive.PatchContext;
import com.riprod.patchly.election.OwnershipElection;
import com.riprod.patchly.reload.AssetReloader;
import com.riprod.patchly.reload.MonitorInstaller;
import com.riprod.patchly.source.SourceKind;
import com.riprod.patchly.source.SourceKindRegistry;
import com.riprod.patchly.source.SourceKindTable;
import com.riprod.patchly.store.OverridePackRegistrar;
import com.riprod.patchly.store.OverrideStore;
import com.riprod.patchly.util.PathUtil;
import com.riprod.patchly.watch.PatchChangeListener;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class PatchManager implements PatchChangeListener {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String PATCHER_VERSION = loadSelfVersion();

    private final JavaPlugin plugin;
    private final OwnershipElection election;
    private final OverrideStore store;
    private final OverridePackRegistrar registrar;
    private final AssetReloader reloader;
    private final MonitorInstaller monitorInstaller;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private final Map<Path, String> patchToTarget = new ConcurrentHashMap<>();
    private final Map<String, List<PatchSource>> packSourceCache = new ConcurrentHashMap<>();
    private volatile boolean monitorInstalled = false;
    private AssetTypeIndex assetLocator;

    private static final int LOOP_BREAKER_THRESHOLD = 10;
    private final Map<String, Integer> consecutiveWrites = new HashMap<>();
    private final Set<String> trippedTargets = ConcurrentHashMap.newKeySet();

    public PatchManager(@Nonnull JavaPlugin plugin) {
        this.plugin = plugin;
        PluginIdentifier owner = new PluginIdentifier(plugin.getManifest());
        String packName = owner.toString() + OverridePackRegistrar.OVERRIDE_PACK_SUFFIX;
        Path overrideDir = PluginManager.MODS_PATH.resolve(
                owner.getGroup() + "_" + owner.getName() + OverridePackRegistrar.OVERRIDE_PACK_SUFFIX);

        this.election = new OwnershipElection(owner.toString(), PATCHER_VERSION);
        this.store = new OverrideStore(overrideDir);
        this.registrar = new OverridePackRegistrar(owner, packName, overrideDir);
        this.reloader = new AssetReloader(overrideDir, packName);
        this.monitorInstaller = new MonitorInstaller(this);
    }

    public void install() {
        if (!election.claim()) return;
        store.wipe();
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
            packSourceCache.remove(name);
            rebuildAndApply("packUnregister:" + name);
        });
        plugin.getEventRegistry().register(ShutdownEvent.class, e -> shutdown());
    }

    public boolean isActive() {
        return election.isActive();
    }

    public String getOverridePackName() {
        return registrar.packName();
    }

    public static boolean isSyntheticOverridePack(@Nonnull String name) {
        return OverridePackRegistrar.isSynthetic(name);
    }

    public synchronized Set<String> rebuildAndApply(@Nonnull String reason) {
        if (!election.isActive()) return Set.of();

        Map<String, JsonObject> desired = compose().outputs();
        if (desired.isEmpty()) {
            LOGGER.at(Level.INFO).log("[patcher] no patches resolved (%s)", reason);
            return Set.of();
        }

        boolean reactive = isReactiveReason(reason);
        List<Path> changed = new ArrayList<>();
        for (Map.Entry<String, JsonObject> entry : desired.entrySet()) {
            String target = entry.getKey();
            if (trippedTargets.contains(target)) continue;
            if (reactive && consecutiveWrites.getOrDefault(target, 0) >= LOOP_BREAKER_THRESHOLD) {
                trippedTargets.add(target);
                LOGGER.at(Level.WARNING).log(
                        "[patcher] loop breaker tripped for %s after %d consecutive rebuilds (%s); "
                                + "freezing output - another tool is likely fighting Patchly over this asset",
                        target, LOOP_BREAKER_THRESHOLD, reason);
                continue;
            }
            Path written = store.writeIfChanged(target, entry.getValue());
            if (written != null) {
                if (reactive) consecutiveWrites.merge(target, 1, Integer::sum);
                changed.add(written);
            } else {
                consecutiveWrites.remove(target);
            }
        }

        if (changed.isEmpty()) {
            LOGGER.at(Level.FINE).log(
                    "[patcher] noop %s - all %d output(s) byte-identical to disk", reason, desired.size());
            return desired.keySet();
        }

        boolean firstRegister = !registrar.isRegistered();
        if (firstRegister) {
            registrar.register();
        }

        reloader.reload(changed);

        if (!monitorInstalled) {
            monitorInstaller.install();
            monitorInstalled = true;
        }
        LOGGER.at(Level.INFO).log(
                "[patcher] %d patch output(s); %d file(s) changed and %s (%s)",
                desired.size(), changed.size(), firstRegister ? "registered" : "reloaded", reason);
        return desired.keySet();
    }

    public void shutdown() {
        if (!election.isActive()) return;
        store.clear(true);
        election.release();
    }

    @Nonnull
    private CompileResult compose() {
        SourceKindTable kinds = SourceKindRegistry.table();
        List<PatchSource> sources = discoverSources(kinds);
        this.assetLocator = new AssetTypeIndex(sources);
        CompileResult result = new PatchCompiler().compile(
                sources, this::resolveBaseJson, buildPatchContext(), JsonDeepMerge.activeTable(), assetLocator);

        for (CompileResult.MissingBase mb : result.missingBases()) {
            LOGGER.at(Level.WARNING).log(
                    "[patcher] no base asset for patch %s (looking for %s)", mb.source(), mb.target());
        }
        for (CompileResult.UnresolvedImport ui : result.unresolvedImports()) {
            LOGGER.at(Level.WARNING).log(
                    "[patcher] unresolved $Import '%s' from %s", ui.ref(), ui.fromTarget());
        }

        patchToTarget.clear();
        patchToTarget.putAll(result.sourceToTarget());
        return result;
    }

    @Nonnull
    private List<PatchSource> discoverSources(@Nonnull SourceKindTable kinds) {
        List<AssetPack> packs = AssetModule.get().getAssetPacks();
        List<PatchSource> out = new ArrayList<>();
        int walked = 0;
        int cached = 0;
        for (int i = 0; i < packs.size(); i++) {
            AssetPack pack = packs.get(i);
            if (isSyntheticOverridePack(pack.getName())) continue;
            List<PatchSource> perPack = packSourceCache.get(pack.getName());
            if (perPack == null) {
                perPack = walkPack(pack, i, kinds);
                packSourceCache.put(pack.getName(), perPack);
                walked++;
            } else {
                cached++;
            }
            for (PatchSource s : perPack) {
                // loadIndex is a position in getAssetPacks() that shifts on register/unregister;
                // re-stamp against the pack current index, copy the record only when it moved
                out.add(s.loadIndex() == i ? s
                        : new PatchSource(s.id(), i, s.targetRelative(), s.kind(), s.patchJson()));
            }
        }
        LOGGER.at(Level.FINE).log("[patcher] discovered %d source(s) (%d pack(s) walked, %d cached)",
                out.size(), walked, cached);
        return out;
    }

    @Nonnull
    private List<PatchSource> walkPack(@Nonnull AssetPack pack, int packIndex, @Nonnull SourceKindTable kinds) {
        List<PatchSource> out = new ArrayList<>();
        final Path root = pack.getRoot();
        try {
            Files.walkFileTree(root, EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            Path name = file.getFileName();
                            SourceKind kind = name == null ? null : kinds.kindFor(name.toString());
                            if (kind == null) return FileVisitResult.CONTINUE;
                            JsonObject json = readJson(file);
                            if (json == null) return FileVisitResult.CONTINUE;
                            String relSource = PathUtil.normalizeRelative(root, file);
                            String stem = PathUtil.stripSuffix(relSource, kind.extension());
                            if (stem == null) return FileVisitResult.CONTINUE;
                            String target = PathUtil.recoverTargetExtension(stem);
                            out.add(new PatchSource(file, packIndex, target, kind, json));
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[patcher] walk failed for pack %s", pack.getName());
        }
        return out;
    }

    @Nullable
    private JsonObject resolveBaseJson(@Nonnull String relativeTarget) {
        AssetTypeIndex locator = this.assetLocator;
        Path base = locator == null ? null : locator.upstreamBasePath(relativeTarget);
        return base == null ? null : readJson(base);
    }

    @Nonnull
    private PatchContext buildPatchContext() {
        return new PatchContext() {
            private Map<String, Semver> present;

            private Map<String, Semver> present() {
                if (present == null) {
                    Map<String, Semver> versions = new HashMap<>();
                    for (AssetPack p : AssetModule.get().getAssetPacks()) {
                        versions.put(p.getName(), p.getManifest().getVersion());
                    }
                    PluginManager pluginManager = PluginManager.get();
                    if (pluginManager != null) {
                        for (PluginBase plugin : pluginManager.getPlugins()) {
                            versions.putIfAbsent(plugin.getIdentifier().toString(),
                                    plugin.getManifest().getVersion());
                        }
                    }
                    present = versions;
                }
                return present;
            }

            @Override
            public boolean packPresent(@Nonnull String packName) {
                return present().containsKey(packName);
            }

            @Override
            public boolean versionSatisfies(@Nonnull String packName, @Nonnull String range) {
                Semver version = present().get(packName);
                if (version == null) return false;
                try {
                    return version.satisfies(SemverRange.fromString(range));
                } catch (RuntimeException e) {
                    return true;
                }
            }
        };
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

    @Override
    public boolean isSourceFile(@Nonnull Path path) {
        Path name = path.getFileName();
        return name != null && SourceKindRegistry.table().claims(name.toString());
    }

    @Override
    public boolean isBaseFile(@Nonnull Path path) {
        return PathUtil.isJsonFile(path);
    }

    @Override
    public void onPatchEvent(@Nonnull AssetPack pack, @Nonnull Path patchFile) {
        if (!election.isActive()) return;
        if (!isSourceFile(patchFile)) return;
        packSourceCache.remove(pack.getName());
        consecutiveWrites.clear();
        trippedTargets.clear();
        if (Files.exists(patchFile)) {
            rebuildAndApply("patchEdit:" + pack.getName() + ":" + patchFile.getFileName());
        } else {
            String target = patchToTarget.get(patchFile);
            Set<String> produced =
                    rebuildAndApply("patchDelete:" + pack.getName() + ":" + patchFile.getFileName());
            if (target != null && !produced.contains(target)) {
                restoreOrDrop(target);
            }
        }
    }

    private void restoreOrDrop(@Nonnull String target) {
        JsonObject base = resolveBaseJson(target);
        if (base == null) {
            store.deleteOverride(target);
            return;
        }
        Path restored = store.writeIfChanged(target, base);
        if (restored != null) reloader.reload(List.of(restored));
    }

    @Override
    public void onBaseEvent(@Nonnull AssetPack pack, @Nonnull Path changedJson) {
        if (!election.isActive()) return;
        rebuildAndApply("baseEdit:" + pack.getName() + ":" + changedJson.getFileName());
    }

    private static boolean isReactiveReason(@Nonnull String reason) {
        return reason.startsWith("baseEdit") || reason.startsWith("packRegister")
                || reason.startsWith("packUnregister");
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
}
