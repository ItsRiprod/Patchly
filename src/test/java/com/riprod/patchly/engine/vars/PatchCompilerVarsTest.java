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
        BaseResolver bases = t -> parse("{}");
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
        CompileResult out = new PatchCompiler().compile(sources, t -> parse("{}"), ALL, TABLE);
        assertEquals(1, out.unresolvedExpressions().size());
        assertTrue(out.outputs().get("X.json").has("Amount#"));
    }
}
