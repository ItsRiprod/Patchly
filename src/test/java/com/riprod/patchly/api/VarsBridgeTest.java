package com.riprod.patchly.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VarsBridgeTest {
    private static final Map<String, Map<String, Double>> ONE = Map.of("Globals", Map.of("On", 1.0));
    private static final Map<String, Map<String, Double>> TWO = Map.of("Globals", Map.of("On", 0.0), "S", Map.of("X", 2.0));

    @BeforeEach
    @AfterEach
    void clearSharedState() {
        System.getProperties().remove(PatchlyVarBridge.SYS_PROP_SNAPSHOT);
        System.getProperties().remove(PatchlyVarBridge.SYS_PROP_READY);
        System.getProperties().remove(PatchlyVarBridge.SYS_PROP_LISTENERS);
    }

    @Test
    void emptyBeforeFirstPublish() {
        assertTrue(PatchlyAPI.get().isEmpty());
        assertFalse(PatchlyAPI.whenReady().isDone());
        assertFalse(PatchlyAPI.getFlag("On"));
        assertEquals(0.0, PatchlyAPI.getNumber("S.X"));
    }

    @Test
    void publishSetsSnapshotAndCompletesReady() {
        PatchlyVarBridge.publish(ONE);
        assertTrue(PatchlyAPI.whenReady().isDone());
        assertEquals(ONE, PatchlyAPI.whenReady().join());
        assertTrue(PatchlyAPI.getFlag("On"));
        assertEquals(1.0, PatchlyAPI.getNumber("Globals.On"));
        assertFalse(PatchlyAPI.getFlag("Global.On"));
        assertThrows(UnsupportedOperationException.class, () -> PatchlyAPI.get().clear());
    }

    @Test
    void identicalPublishFiresNoListener() {
        List<Map<String, Map<String, Double>>> seen = new ArrayList<>();
        PatchlyAPI.onChange(seen::add);
        PatchlyVarBridge.publish(ONE);
        PatchlyVarBridge.publish(Map.of("Globals", Map.of("On", 1.0)));
        assertEquals(1, seen.size());
    }

    @Test
    void differingPublishFiresOnceWithNewMap() {
        List<Map<String, Map<String, Double>>> seen = new ArrayList<>();
        PatchlyAPI.onChange(seen::add);
        PatchlyVarBridge.publish(ONE);
        PatchlyVarBridge.publish(TWO);
        assertEquals(2, seen.size());
        assertEquals(TWO, seen.get(1));
        assertSame(PatchlyAPI.get(), seen.get(1));
        assertEquals(2.0, PatchlyAPI.getNumber("S.X"));
    }

    @Test
    void emptyFirstPublishStillCompletesReady() {
        PatchlyVarBridge.publish(Map.of());
        assertTrue(PatchlyAPI.whenReady().isDone());
        assertTrue(PatchlyAPI.whenReady().join().isEmpty());
    }

    @Test
    void legacyCompletionYieldsEmptyMap() {
        PatchlyVarBridge.completeEmptyIfLegacy();
        assertTrue(PatchlyAPI.whenReady().isDone());
        assertTrue(PatchlyAPI.whenReady().join().isEmpty());
        PatchlyVarBridge.publish(ONE);
        assertTrue(PatchlyAPI.whenReady().join().isEmpty());
        assertTrue(PatchlyAPI.getFlag("On"));
    }

    @Test
    void throwingListenerDoesNotBlockOthers() {
        List<Integer> order = new ArrayList<>();
        PatchlyAPI.onChange(m -> { throw new IllegalStateException("boom"); });
        PatchlyAPI.onChange(m -> order.add(2));
        PatchlyVarBridge.publish(ONE);
        assertEquals(List.of(2), order);
    }

    @Test
    void sharedObjectsSurviveAcrossLookups() {
        var ref = PatchlyVarBridge.snapshotRef();
        var ready = PatchlyVarBridge.ready();
        var listeners = PatchlyVarBridge.listeners();
        assertSame(ref, PatchlyVarBridge.snapshotRef());
        assertSame(ready, PatchlyVarBridge.ready());
        assertSame(listeners, PatchlyVarBridge.listeners());
    }
}
