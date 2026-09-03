package com.riprod.patchly.engine.vars;

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
import com.riprod.patchly.source.kinds.VarsKind;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchCompilerVarsTest {
    private static final MergeTable TABLE = JsonDeepMerge.activeTable();
    private static final SourceKind PATCH = new PatchKind();
    private static final SourceKind VARS = new VarsKind();
    private static final PatchContext ALL = new PatchContext() {
        @Override
        public boolean packPresent(@Nonnull String packName) {
            return true;
        }

        @Override
        public boolean versionSatisfies(@Nonnull String packName, @Nonnull String range) {
            return true;
        }
    };

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static PatchSource src(String id, int idx, String target, SourceKind kind, String json) {
        return new PatchSource(Path.of(id), idx, target, kind, parse(json));
    }

    @Test
    void resolvesExpressionsAndExcludesEnvFromOutputs() {
        List<PatchSource> sources = List.of(
                src("Globals.vars", 0, "Globals.vars", VARS, "{ \"Mana\": 40, \"Head\": 0.25 }"),
                src("Armor_Adamantite_Head.patch", 1, "Armor_Adamantite_Head.json", PATCH,
                        "{ \"Mana?\": [ { \"Amount#\": \"$Mana * $Head\", \"CalculationType\": \"Additive\" } ] }"));
        BaseResolver bases = t -> new BaseResolver.ResolvedBase(t, parse("{}"));
        CompileResult out = new PatchCompiler().compile(sources, bases, ALL, TABLE);

        assertTrue(out.unresolvedExpressions().isEmpty());
        assertFalse(out.outputs().containsKey("Globals.vars"));
        JsonObject armor = out.outputs().get("Armor_Adamantite_Head.json");
        assertNotNull(armor);
        JsonObject el = armor.getAsJsonArray("Mana").get(0).getAsJsonObject();
        assertEquals(10, el.get("Amount").getAsInt());
    }

    @Test
    void badExpressionReportedInChannel() {
        List<PatchSource> sources = List.of(
                src("Globals.vars", 0, "Globals.vars", VARS, "{ \"Mana\": 40 }"),
                src("X.patch", 1, "X.json", PATCH, "{ \"Amount#\": \"$Nope\" }"));
        CompileResult out = new PatchCompiler().compile(sources, t -> new BaseResolver.ResolvedBase(t, parse("{}")), ALL, TABLE);
        assertEquals(1, out.unresolvedExpressions().size());
        assertEquals("X.json", out.unresolvedExpressions().get(0).target());
        assertTrue(out.outputs().get("X.json").has("Amount#"));
    }

    private static final PatchContext ONLY_A = new PatchContext() {
        @Override
        public boolean packPresent(@Nonnull String packName) {
            return packName.equals("Author:A");
        }

        @Override
        public boolean versionSatisfies(@Nonnull String packName, @Nonnull String range) {
            return packPresent(packName);
        }
    };

    private static CompileResult compile(PatchContext ctx, PatchSource... sources) {
        return new PatchCompiler().compile(List.of(sources),
                t -> new BaseResolver.ResolvedBase(t, parse("{}")), ctx, TABLE);
    }

    private static boolean gated(CompileResult out, String sourceId) {
        for (CompileResult.GatedSource gs : out.gatedSources()) {
            if (gs.source().toString().equals(sourceId)) return true;
        }
        return false;
    }

    @Test
    void patchGatedByGlobalFlag() {
        CompileResult out = compile(ONLY_A,
                src("Globals.vars", 0, "Globals.vars", VARS, "{ \"Enabled\": true, \"Disabled\": false }"),
                src("On.patch", 1, "On.json", PATCH, "{ \"$Requires\": \"$Enabled\", \"K\": 1 }"),
                src("Off.patch", 1, "Off.json", PATCH, "{ \"$Requires\": \"$Disabled\", \"K\": 1 }"),
                src("Not.patch", 1, "Not.json", PATCH, "{ \"$Requires\": \"-$Disabled\", \"K\": 1 }"));
        assertTrue(out.outputs().containsKey("On.json"));
        assertFalse(out.outputs().containsKey("Off.json"));
        assertTrue(out.outputs().containsKey("Not.json"));
        assertTrue(gated(out, "Off.patch"));
        assertTrue(out.unresolvedExpressions().isEmpty());
    }

    @Test
    void patchGatedByScopedExpression() {
        CompileResult out = compile(ONLY_A,
                src("Globals.vars", 0, "Globals.vars", VARS, "{ \"Mult\": 2 }"),
                src("Adamantite.vars", 0, "Adamantite.vars", VARS, "{ \"Power\": \"$Mult * 3\" }"),
                src("X.patch", 1, "X.json", PATCH, "{ \"$Requires\": [\"$Adamantite.Power - 5\", \"Author:A\"], \"K\": 1 }"),
                src("Y.patch", 1, "Y.json", PATCH, "{ \"$Requires\": [\"$Adamantite.Power - 6\"], \"K\": 1 }"));
        assertTrue(out.outputs().containsKey("X.json"));
        assertFalse(out.outputs().containsKey("Y.json"));
    }

    @Test
    void globalsGatedByPacksOnly() {
        CompileResult out = compile(ONLY_A,
                src("A/Globals.vars", 0, "A/Globals.vars", VARS, "{ \"$Requires\": \"Author:A\", \"FromA\": 1 }"),
                src("B/Globals.vars", 0, "B/Globals.vars", VARS, "{ \"$Requires\": \"Author:B\", \"FromB\": 1 }"),
                src("C/Globals.vars", 0, "C/Globals.vars", VARS, "{ \"$Requires\": \"$FromA\", \"FromC\": 1 }"));
        var globals = out.vars().get("Globals");
        assertEquals(1.0, globals.get("FromA"));
        assertFalse(globals.containsKey("FromB"));
        assertFalse(globals.containsKey("FromC"));
        assertTrue(gated(out, "C/Globals.vars"));
        assertEquals(1, out.unresolvedExpressions().size());
        CompileResult.UnresolvedExpression ue = out.unresolvedExpressions().get(0);
        assertEquals("C/Globals.vars", ue.where());
        assertEquals("$FromA", ue.expression());
        assertEquals("C/Globals.vars", ue.target());
    }

    @Test
    void scopedVarsGatedByGlobalsAndMissingScopeIsJoinable() {
        CompileResult out = compile(ONLY_A,
                src("Globals.vars", 0, "Globals.vars", VARS, "{ \"UseIron\": false }"),
                src("Iron.vars", 0, "Iron.vars", VARS, "{ \"$Requires\": \"$UseIron\", \"Power\": 4 }"),
                src("Steel.vars", 0, "Steel.vars", VARS, "{ \"$Requires\": \"-$UseIron\", \"Power\": 9 }"),
                src("X.patch", 1, "X.json", PATCH, "{ \"Amount#\": \"$Iron.Power\", \"Other#\": \"$Steel.Power\" }"));
        assertFalse(out.vars().containsKey("Iron"));
        assertEquals(9.0, out.vars().get("Steel").get("Power"));
        assertTrue(gated(out, "Iron.vars"));
        assertEquals(1, out.unresolvedExpressions().size());
        CompileResult.UnresolvedExpression ue = out.unresolvedExpressions().get(0);
        assertEquals("Iron", ue.missingScope());
        assertEquals("X.json", ue.target());
        assertEquals(9, out.outputs().get("X.json").get("Other").getAsInt());
    }

    @Test
    void batchGatedByFlag() {
        SourceKind batch = new BatchKind();
        CompileResult out = compile(ONLY_A,
                src("Globals.vars", 0, "Globals.vars", VARS, "{ \"Heavy\": 0 }"),
                src("Dir/_.batch", 1, "", batch, "{ \"$Requires\": \"$Heavy\", \"Weight\": 10 }"),
                src("Dir/X.patch", 1, "X.json", PATCH, "{ \"K\": 1 }"));
        assertFalse(out.outputs().containsKey("X.json"));
        assertTrue(gated(out, "Dir/X.patch"));

        CompileResult on = compile(ONLY_A,
                src("Globals.vars", 0, "Globals.vars", VARS, "{ \"Heavy\": 1 }"),
                src("Dir/_.batch", 1, "", batch, "{ \"$Requires\": \"$Heavy\", \"Weight\": 10 }"),
                src("Dir/X.patch", 1, "X.json", PATCH, "{ \"K\": 1 }"));
        assertEquals(10, on.outputs().get("X.json").get("Weight").getAsInt());
    }

    @Test
    void nestedArrayElementGatedByFlag() {
        CompileResult out = compile(ONLY_A,
                src("Globals.vars", 0, "Globals.vars", VARS, "{ \"Extra\": true }"),
                src("X.patch", 1, "X.json", PATCH,
                        "{ \"List+\": [ { \"$Requires\": \"$Extra\", \"Id\": \"a\" }, { \"$Requires\": \"-$Extra\", \"Id\": \"b\" } ] }"));
        var list = out.outputs().get("X.json").getAsJsonArray("List");
        assertEquals(1, list.size());
        assertEquals("a", list.get(0).getAsJsonObject().get("Id").getAsString());
    }

    @Test
    void mergeExpressionErrorsAreStampedWithTheWritePath() {
        List<PatchSource> sources = List.of(
                src("Sub/X.patch", 1, "Sub/X.json", PATCH, "{ \"Amount#\": \"$Nope\" }"));
        CompileResult out = new PatchCompiler().compile(sources,
                t -> new BaseResolver.ResolvedBase("Server/Real/X.json", parse("{}")), ONLY_A, TABLE);
        assertEquals(1, out.unresolvedExpressions().size());
        assertEquals("Server/Real/X.json", out.unresolvedExpressions().get(0).target());
        assertTrue(out.outputs().containsKey("Server/Real/X.json"));
    }

    @Test
    void varsShapeAlwaysHasGlobals() {
        CompileResult out = compile(ONLY_A, src("X.patch", 1, "X.json", PATCH, "{ \"K\": 1 }"));
        assertTrue(out.vars().containsKey("Globals"));
        assertTrue(out.vars().get("Globals").isEmpty());
    }
}
