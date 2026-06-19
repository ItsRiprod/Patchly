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
import com.riprod.patchly.source.kinds.PatchKind;
import com.riprod.patchly.source.kinds.PutKind;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchCompilerTest {
    private static final MergeTable TABLE = JsonDeepMerge.activeTable();
    private static final SourceKind PATCH = new PatchKind();
    private static final SourceKind PUT = new PutKind();

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

    private static CompileResult compile(List<PatchSource> sources, BaseResolver bases, PatchContext ctx) {
        return new PatchCompiler().compile(sources, bases, ctx, TABLE);
    }

    @Test
    void nestedRequiresGatesElementWhenAbsent() {
        CompileResult out = compile(
                List.of(source("a.patch", 0, "Foo.json", PATCH,
                        "{ \"Children~\": [ { \"$Requires\": \"Some:Mod\", \"$Match\": \"Id\", \"Id\": \"Cloth\", \"Name\": \"patched\" } ] }")),
                t -> parse("{ \"Children\": [ { \"Id\": \"Cloth\", \"Name\": \"base\" } ] }"), NONE_PRESENT);
        JsonObject cloth = out.outputs().get("Foo.json").getAsJsonArray("Children").get(0).getAsJsonObject();
        assertEquals("base", cloth.get("Name").getAsString());
        assertFalse(out.outputs().get("Foo.json").toString().contains("$Requires"));
    }

    @Test
    void nestedRequiresAppliesAndStripsWhenPresent() {
        CompileResult out = compile(
                List.of(source("a.patch", 0, "Foo.json", PATCH,
                        "{ \"Children~\": [ { \"$Requires\": \"Some:Mod\", \"$Match\": \"Id\", \"Id\": \"Cloth\", \"Name\": \"patched\" } ] }")),
                t -> parse("{ \"Children\": [ { \"Id\": \"Cloth\", \"Name\": \"base\" } ] }"), ALL_PRESENT);
        JsonObject cloth = out.outputs().get("Foo.json").getAsJsonArray("Children").get(0).getAsJsonObject();
        assertEquals("patched", cloth.get("Name").getAsString());
        assertFalse(out.outputs().get("Foo.json").toString().contains("$Requires"));
    }

    @Test
    void nestedObjectRequiresGatesFieldWhenAbsent() {
        // $Requires inside a nested object value: missing pack leaves the base field
        // untouched
        CompileResult out = compile(
                List.of(source("a.patch", 0, "Foo.json", PATCH,
                        "{ \"TranslationProperties\": { \"$Requires\": \"Some:Mod\", \"Name\": \"patched\" } }")),
                t -> parse("{ \"TranslationProperties\": { \"Name\": \"base\" } }"), NONE_PRESENT);
        JsonObject tp = out.outputs().get("Foo.json").getAsJsonObject("TranslationProperties");
        assertEquals("base", tp.get("Name").getAsString());
        assertFalse(out.outputs().get("Foo.json").toString().contains("$Requires"));
    }

    @Test
    void nestedObjectRequiresAppliesAndStripsWhenPresent() {
        CompileResult out = compile(
                List.of(source("a.patch", 0, "Foo.json", PATCH,
                        "{ \"TranslationProperties\": { \"$Requires\": \"Some:Mod\", \"Name\": \"patched\" } }")),
                t -> parse("{ \"TranslationProperties\": { \"Name\": \"base\" } }"), ALL_PRESENT);
        JsonObject tp = out.outputs().get("Foo.json").getAsJsonObject("TranslationProperties");
        assertEquals("patched", tp.get("Name").getAsString());
        assertFalse(out.outputs().get("Foo.json").toString().contains("$Requires"));
    }

    @Test
    void requiresMissingPackExcludesSource() {
        CompileResult out = compile(
                List.of(source("a.patch", 0, "Foo.json", PATCH,
                        "{ \"$Requires\": \"Some:Mod\", \"Tier\": 3 }")),
                t -> parse("{ \"Tier\": 1 }"), NONE_PRESENT);
        assertTrue(out.outputs().isEmpty());
    }

    @Test
    void priorityWinsOverLoadOrder() {
        // lower load index would normally apply last; higher $Priority overrides that
        // ordering
        CompileResult out = compile(List.of(
                source("late.patch", 9, "Foo.json", PATCH, "{ \"$Priority\": 100, \"V\": \"high\" }"),
                source("early.patch", 0, "Foo.json", PATCH, "{ \"V\": \"low\" }")), t -> parse("{}"), ALL_PRESENT);
        assertEquals("high", out.outputs().get("Foo.json").get("V").getAsString());
    }

    @Test
    void patchKindRecordsMissingBase() {
        CompileResult out = compile(
                List.of(source("a.patch", 0, "Foo.json", PATCH, "{ \"Tier\": 3 }")),
                t -> null, ALL_PRESENT);
        assertTrue(out.outputs().isEmpty());
        assertEquals(1, out.missingBases().size());
        assertEquals("Foo.json", out.missingBases().get(0).target());
    }

    @Test
    void putKindSeedsEmptyWhenBaseMissing() {
        CompileResult out = compile(
                List.of(source("a.particlespawner.put", 0, "Foo.particlespawner", PUT, "{ \"Color\": \"white\" }")),
                t -> null, ALL_PRESENT);
        assertTrue(out.missingBases().isEmpty());
        JsonObject result = out.outputs().get("Foo.particlespawner");
        assertEquals("white", result.get("Color").getAsString());
    }

    @Test
    void putKindGatedByRequiresNotCreatedWhenAbsent() {
        CompileResult out = compile(
                List.of(source("a.put", 0, "Foo.json", PUT, "{ \"$Requires\": \"Some:Mod\", \"Color\": \"white\" }")),
                t -> null, NONE_PRESENT);
        assertTrue(out.outputs().isEmpty());
        assertTrue(out.missingBases().isEmpty());
    }

    @Test
    void putKindMergesOntoExistingBaseAndFillRespectsBase() {
        CompileResult out = compile(
                List.of(source("a.put", 0, "Foo.json", PUT, "{ \"Color?\": \"white\", \"Tier\": 3 }")),
                t -> parse("{ \"Color\": \"red\" }"), ALL_PRESENT);
        JsonObject result = out.outputs().get("Foo.json");
        assertEquals("red", result.get("Color").getAsString());
        assertEquals(3, result.get("Tier").getAsInt());
    }

    @Test
    void bareArrayElementMarkersStrippedFromOutput() {
        CompileResult out = compile(
                List.of(source("a.put", 0, "Foo.json", PUT,
                        "{ \"Stats\": [ { \"$Priority\": 5, \"$Requires\": \"Some:Mod\", \"Amount\": 1 } ] }")),
                t -> null, ALL_PRESENT);
        JsonObject r = out.outputs().get("Foo.json");
        assertFalse(r.toString().contains("$Requires"));
        assertFalse(r.toString().contains("$Priority"));
        assertEquals(1, r.getAsJsonArray("Stats").get(0).getAsJsonObject().get("Amount").getAsInt());
    }

    @Test
    void bareArrayElementGatedOutWhenPackAbsent() {
        CompileResult out = compile(
                List.of(source("a.put", 0, "Foo.json", PUT,
                        "{ \"Stats\": [ { \"$Requires\": \"Some:Mod\", \"Amount\": 1 } ] }")),
                t -> null, NONE_PRESENT);
        assertEquals(0, out.outputs().get("Foo.json").getAsJsonArray("Stats").size());
    }

    @Test
    void accumulatesMultipleSourcesIntoOneTarget() {
        CompileResult out = compile(List.of(
                source("a.patch", 0, "Foo.json", PATCH, "{ \"A\": 1 }"),
                source("b.patch", 1, "Foo.json", PATCH, "{ \"B\": 2 }")),
                t -> parse("{ \"Base\": true }"), ALL_PRESENT);
        JsonObject result = out.outputs().get("Foo.json");
        assertTrue(result.get("Base").getAsBoolean());
        assertEquals(1, result.get("A").getAsInt());
        assertEquals(2, result.get("B").getAsInt());
        assertFalse(result.has("$Priority"));
        Map<Path, String> tracking = out.sourceToTarget();
        assertEquals("Foo.json", tracking.get(Path.of("a.patch")));
    }
}
