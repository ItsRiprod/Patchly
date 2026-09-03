package com.riprod.patchly.api;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class PatchlyAPI {
    private static final String GLOBAL_SCOPE = "Globals";

    private PatchlyAPI() {}

    public static boolean getFlag(@Nonnull String ref) {
        return getNumber(ref) > 0;
    }

    public static double getNumber(@Nonnull String ref) {
        Double value = resolve(PatchlyVarBridge.snapshot(), ref);
        return value == null ? 0 : value;
    }

    @Nonnull
    public static Map<String, Map<String, Double>> get() {
        return PatchlyVarBridge.snapshot();
    }

    @Nonnull
    public static CompletableFuture<Map<String, Map<String, Double>>> whenReady() {
        return PatchlyVarBridge.ready();
    }

    public static void onChange(@Nonnull Consumer<Map<String, Map<String, Double>>> listener) {
        PatchlyVarBridge.addListener(listener);
    }

    private static Double resolve(@Nonnull Map<String, Map<String, Double>> vars, @Nonnull String ref) {
        int dot = ref.indexOf('.');
        String scope = dot < 0 ? GLOBAL_SCOPE : ref.substring(0, dot);
        String name = dot < 0 ? ref : ref.substring(dot + 1);
        Map<String, Double> members = vars.get(scope);
        return members == null ? null : members.get(name);
    }
}
