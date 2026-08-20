package com.riprod.patchly.engine.compile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.riprod.patchly.core.JsonDeepMerge;
import com.riprod.patchly.core.MergeTable;
import com.riprod.patchly.core.compile.BaseResolver;
import com.riprod.patchly.core.compile.CompileResult;
import com.riprod.patchly.core.compile.PatchCompiler;
import com.riprod.patchly.core.compile.PatchSource;
import com.riprod.patchly.core.directive.PatchContext;
import com.riprod.patchly.source.SourceKind;
import com.riprod.patchly.source.kinds.BatchKind;
import com.riprod.patchly.source.kinds.PatchKind;
import com.riprod.patchly.source.kinds.PutKind;
import com.riprod.patchly.source.kinds.VarsKind;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchCompilerTest {
    private static final MergeTable TABLE = JsonDeepMerge.activeTable();
    private static final SourceKind PATCH = new PatchKind();
    private static final SourceKind PUT = new PutKind();
    private static final SourceKind VARS = new VarsKind();
    private static final SourceKind BATCH = new BatchKind();

    private static final String DIR = "Server/Item/Items/Patches/Hexcode/";
    private static final String NESTED = DIR + "Heavy/";
    private static final String SIBLING = "Server/Item/Items/Patches/Vanilla/";

    private static final PatchContext ALL_PRESENT = new PatchContext() {
        @Override
        public boolean packPresent(@Nonnull String packName) {
            return true;
        }

        @Override
        public boolean versionSatisfies(@Nonnull String packName, @Nonnull String range) {
            return true;
        }
    };

    private static final PatchContext NONE_PRESENT = new PatchContext() {
        @Override
        public boolean packPresent(@Nonnull String packName) {
            return false;
        }

        @Override
        public boolean versionSatisfies(@Nonnull String packName, @Nonnull String range) {
            return false;
        }
    };

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static PatchSource source(String id, int loadIndex, String target, SourceKind kind, String json) {
        return new PatchSource(Path.of(id), loadIndex, target, kind, parse(json));
    }

    private static PatchSource batch(String id, String json) {
        return source(id, 0, "", BATCH, json);
    }

    private static CompileResult compile(List<PatchSource> sources, BaseResolver bases, PatchContext ctx) {
        return new PatchCompiler().compile(sources, bases, ctx, TABLE);
    }

    private static BaseResolver bases(Map<String, String> map) {
        return target -> map.containsKey(target) ? parse(map.get(target)) : null;
    }

    @Test
    void unsatisfiedBatchGatesEverySourceInSubtree() {
        CompileResult out = compile(List.of(
                batch(DIR + "_.batch", "{ \"$Requires\": \"Some:Mod\" }"),
                source(DIR + "Head.patch", 0, "Head.json", PATCH, "{ \"Tier\": 3 }"),
                source(NESTED + "Chest.patch", 0, "Chest.json", PATCH, "{ \"Tier\": 4 }")),
                bases(Map.of("Head.json", "{}", "Chest.json", "{}")), NONE_PRESENT);

        assertTrue(out.outputs().isEmpty());
        assertEquals(3, out.gatedSources().size());
    }

    @Test
    void gatedSourceNamesTheBatchThatScopedIt() {
        CompileResult out = compile(List.of(
                batch(DIR + "_.batch", "{ \"$Requires\": \"Some:Mod\" }"),
                source(DIR + "Head.patch", 0, "Head.json", PATCH, "{ \"Tier\": 3 }")),
                bases(Map.of("Head.json", "{}")), NONE_PRESENT);

        CompileResult.GatedSource scoped = out.gatedSources().stream()
                .filter(g -> g.source().equals(Path.of(DIR + "Head.patch")))
                .findFirst().orElseThrow();
        assertEquals(Path.of(DIR + "_.batch"), scoped.scope());
        assertEquals("$Requires", scoped.directive());
    }

    @Test
    void siblingDirectoryIsUnaffected() {
        CompileResult out = compile(List.of(
                batch(DIR + "_.batch", "{ \"$Requires\": \"Some:Mod\" }"),
                source(DIR + "Head.patch", 0, "Head.json", PATCH, "{ \"Tier\": 3 }"),
                source(SIBLING + "Boots.patch", 0, "Boots.json", PATCH, "{ \"Tier\": 9 }")),
                bases(Map.of("Head.json", "{}", "Boots.json", "{}")), NONE_PRESENT);

        assertFalse(out.outputs().containsKey("Head.json"));
        assertEquals(9, out.outputs().get("Boots.json").get("Tier").getAsInt());
    }

    @Test
    void nestedBatchCannotWidenAnOuterGate() {
        CompileResult out = compile(List.of(
                batch(DIR + "_.batch", "{ \"$Requires\": \"Some:Mod\" }"),
                batch(NESTED + "_.batch", "{ \"Tag\": \"heavy\" }"),
                source(NESTED + "Chest.patch", 0, "Chest.json", PATCH, "{ \"Tier\": 4 }")),
                bases(Map.of("Chest.json", "{}")), NONE_PRESENT);

        assertTrue(out.outputs().isEmpty());
    }

    @Test
    void bodyMergesIntoEveryInScopeTarget() {
        CompileResult out = compile(List.of(
                batch(DIR + "_.batch", "{ \"Tags\": { \"Imbuement+\": [\"Slot\"] } }"),
                source(DIR + "Head.patch", 0, "Head.json", PATCH, "{ \"Tier\": 3 }"),
                source(NESTED + "Chest.patch", 0, "Chest.json", PATCH, "{ \"Tier\": 4 }")),
                bases(Map.of("Head.json", "{ \"Tags\": { \"Imbuement\": [] } }",
                        "Chest.json", "{ \"Tags\": { \"Imbuement\": [] } }")), ALL_PRESENT);

        for (String target : List.of("Head.json", "Chest.json")) {
            assertEquals(1, out.outputs().get(target)
                    .getAsJsonObject("Tags").getAsJsonArray("Imbuement").size(), target);
        }
    }

    @Test
    void bodyAppliesOnceWhenTwoSourcesShareOneTarget() {
        CompileResult out = compile(List.of(
                batch(DIR + "_.batch", "{ \"Tags\": { \"Imbuement+\": [\"Slot\"] } }"),
                source(DIR + "A.patch", 0, "Head.json", PATCH, "{ \"Tier\": 3 }"),
                source(DIR + "B.patch", 0, "Head.json", PATCH, "{ \"Weight\": 5 }")),
                bases(Map.of("Head.json", "{ \"Tags\": { \"Imbuement\": [] } }")), ALL_PRESENT);

        assertEquals(1, out.outputs().get("Head.json")
                .getAsJsonObject("Tags").getAsJsonArray("Imbuement").size());
    }

    @Test
    void batchProducesNoTargetOfItsOwn() {
        CompileResult out = compile(List.of(
                batch(DIR + "_.batch", "{ \"Tags\": { \"Imbuement+\": [\"Slot\"] } }"),
                source(DIR + "Head.patch", 0, "Head.json", PATCH, "{ \"Tier\": 3 }")),
                bases(Map.of("Head.json", "{}")), ALL_PRESENT);

        assertEquals(1, out.outputs().size());
        assertTrue(out.outputs().containsKey("Head.json"));
        assertFalse(out.sourceToTarget().containsKey(Path.of(DIR + "_.batch")));
    }

    @Test
    void batchNeverCreatesATargetWithoutABase() {
        CompileResult out = compile(List.of(
                batch(DIR + "_.batch", "{ \"Tags\": [\"Slot\"] }"),
                source(DIR + "Head.patch", 0, "Head.json", PATCH, "{ \"Tier\": 3 }")),
                t -> null, ALL_PRESENT);

        assertTrue(out.outputs().isEmpty());
        assertEquals(1, out.missingBases().size());
    }

    @Test
    void inScopeVarsStillResolveWhenTheBatchIsGatedOut() {
        CompileResult out = compile(List.of(
                batch(DIR + "_.batch", "{ \"$Requires\": \"Some:Mod\" }"),
                source(DIR + "Hex.vars", 0, "", VARS, "{ \"Mana\": 80 }"),
                source(SIBLING + "Boots.patch", 0, "Boots.json", PATCH,
                        "{ \"Amount#\": \"$Hex.Mana\" }")),
                bases(Map.of("Boots.json", "{}")), NONE_PRESENT);

        assertTrue(out.unresolvedExpressions().isEmpty());
        assertEquals(80, out.outputs().get("Boots.json").get("Amount").getAsInt());
    }

    @Test
    void siblingPatchOverridesBatchBodyAtEqualPriority() {
        CompileResult out = compile(List.of(
                batch(DIR + "_.batch", "{ \"Quality\": \"Rare\" }"),
                source(DIR + "Head.patch", 0, "Head.json", PATCH, "{ \"Quality\": \"Epic\" }")),
                bases(Map.of("Head.json", "{ \"Quality\": \"Common\" }")), ALL_PRESENT);

        assertEquals("Epic", out.outputs().get("Head.json").get("Quality").getAsString());
    }

    @Test
    void higherPriorityBatchWinsOverSiblingPatch() {
        CompileResult out = compile(List.of(
                batch(DIR + "_.batch", "{ \"$Priority\": 100, \"Quality\": \"Rare\" }"),
                source(DIR + "Head.patch", 0, "Head.json", PATCH, "{ \"Quality\": \"Epic\" }")),
                bases(Map.of("Head.json", "{ \"Quality\": \"Common\" }")), ALL_PRESENT);

        assertEquals("Rare", out.outputs().get("Head.json").get("Quality").getAsString());
    }

    @Test
    void innerBatchOverridesOuterBatch() {
        CompileResult out = compile(List.of(
                batch(DIR + "_.batch", "{ \"Quality\": \"Rare\" }"),
                batch(NESTED + "_.batch", "{ \"Quality\": \"Legendary\" }"),
                source(NESTED + "Chest.patch", 0, "Chest.json", PATCH, "{ \"Tier\": 4 }")),
                bases(Map.of("Chest.json", "{}")), ALL_PRESENT);

        assertEquals("Legendary", out.outputs().get("Chest.json").get("Quality").getAsString());
    }

    @Test
    void batchReachesAPutCreatedTarget() {
        CompileResult out = compile(List.of(
                batch(DIR + "_.batch", "{ \"Quality\": \"Rare\" }"),
                source(DIR + "New.put", 0, "New.json", PUT, "{ \"Tier\": 1 }")),
                t -> null, ALL_PRESENT);

        JsonObject created = out.outputs().get("New.json");
        assertEquals("Rare", created.get("Quality").getAsString());
        assertEquals(1, created.get("Tier").getAsInt());
    }

    @Test
    void contributionsListSourcesInMergeOrder() {
        CompileResult out = compile(List.of(
                batch(DIR + "_.batch", "{ \"Quality\": \"Rare\" }"),
                source(DIR + "Head.patch", 0, "Head.json", PATCH, "{ \"Tier\": 3 }")),
                bases(Map.of("Head.json", "{}")), ALL_PRESENT);

        List<CompileResult.Contribution> applied = out.contributions().get("Head.json");
        assertEquals(2, applied.size());
        assertEquals(".batch", applied.get(0).kind());
        assertEquals(Path.of(DIR + "_.batch"), applied.get(0).source());
        assertEquals(".patch", applied.get(1).kind());
    }

    @Test
    void batchWithoutDirectivesOrBodyIsInert() {
        CompileResult out = compile(List.of(
                batch(DIR + "_.batch", "{}"),
                source(DIR + "Head.patch", 0, "Head.json", PATCH, "{ \"Tier\": 3 }")),
                bases(Map.of("Head.json", "{ \"Tier\": 1 }")), ALL_PRESENT);

        assertEquals(3, out.outputs().get("Head.json").get("Tier").getAsInt());
        assertEquals(1, out.outputs().size());
    }
}
