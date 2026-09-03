package com.riprod.patchly.api;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PatchlyVarBridge {
    static final String SYS_PROP_SNAPSHOT = "patcher.vars.snapshot";
    static final String SYS_PROP_READY = "patcher.vars.ready";
    static final String SYS_PROP_LISTENERS = "patcher.vars.listeners";

    private static final Logger LOGGER = Logger.getLogger("Patchly");

    private PatchlyVarBridge() {}

    @Nonnull
    @SuppressWarnings("unchecked")
    static AtomicReference<Map<String, Map<String, Double>>> snapshotRef() {
        return (AtomicReference<Map<String, Map<String, Double>>>) shared(SYS_PROP_SNAPSHOT,
                () -> new AtomicReference<Map<String, Map<String, Double>>>(Map.of()));
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    static CompletableFuture<Map<String, Map<String, Double>>> ready() {
        return (CompletableFuture<Map<String, Map<String, Double>>>) shared(SYS_PROP_READY,
                CompletableFuture::new);
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    static List<Consumer<Map<String, Map<String, Double>>>> listeners() {
        return (List<Consumer<Map<String, Map<String, Double>>>>) shared(SYS_PROP_LISTENERS,
                CopyOnWriteArrayList::new);
    }

    @Nonnull
    static Map<String, Map<String, Double>> snapshot() {
        return snapshotRef().get();
    }

    static void addListener(@Nonnull Consumer<Map<String, Map<String, Double>>> listener) {
        listeners().add(listener);
    }

    public static void publish(@Nonnull Map<String, Map<String, Double>> vars) {
        Map<String, Map<String, Double>> frozen = freeze(vars);
        AtomicReference<Map<String, Map<String, Double>>> ref = snapshotRef();
        if (ref.get().equals(frozen)) {
            ready().complete(frozen);
            return;
        }
        ref.set(frozen);
        ready().complete(frozen);
        for (Consumer<Map<String, Map<String, Double>>> listener : listeners()) {
            try {
                listener.accept(frozen);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[patcher] vars change listener threw", t);
            }
        }
    }

    public static void completeEmptyIfLegacy() {
        ready().complete(Map.of());
    }

    @Nonnull
    private static Object shared(@Nonnull String key, @Nonnull java.util.function.Supplier<Object> create) {
        Properties props = System.getProperties();
        return props.computeIfAbsent(key, k -> create.get());
    }

    @Nonnull
    private static Map<String, Map<String, Double>> freeze(@Nonnull Map<String, Map<String, Double>> vars) {
        Map<String, Map<String, Double>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Double>> scope : vars.entrySet()) {
            out.put(scope.getKey(), Map.copyOf(scope.getValue()));
        }
        return Map.copyOf(out);
    }
}
