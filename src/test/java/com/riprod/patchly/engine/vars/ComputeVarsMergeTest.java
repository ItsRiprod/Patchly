package com.riprod.patchly.engine.vars;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.riprod.patchly.core.JsonDeepMerge;
import com.riprod.patchly.core.MergeTable;
import com.riprod.patchly.core.compile.CompileResult.UnresolvedExpression;
import com.riprod.patchly.core.compile.PatchSource;
import com.riprod.patchly.core.vars.ComputeOperator;
import com.riprod.patchly.core.vars.VarEnv;
import com.riprod.patchly.core.vars.VarEnvBuilder;
import com.riprod.patchly.source.kinds.VarsKind;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComputeVarsMergeTest {
    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static VarEnv env(String globalsJson) {
        return VarEnvBuilder.build(
                List.of(new PatchSource(Path.of("Globals.vars"), 0, "Globals.vars", new VarsKind(), parse(globalsJson))),
                new ArrayList<>());
    }

    private static JsonObject merge(VarEnv env, List<UnresolvedExpression> errors, String base, String patch) {
        MergeTable table = JsonDeepMerge.activeTable().with(new ComputeOperator(env, errors));
        return JsonDeepMerge.merge(parse(base), parse(patch), table);
    }

    @Test
    void computesTopLevelScalar() {
        List<UnresolvedExpression> errors = new ArrayList<>();
        JsonObject out = merge(env("{ \"Mana\": 5 }"), errors, "{}", "{ \"Amount#\": \"$Mana * 2\" }");
        assertTrue(errors.isEmpty());
        assertFalse(out.has("Amount#"));
        assertEquals(10, out.get("Amount").getAsInt());
    }

    @Test
    void computesInsideFillArray() {
        List<UnresolvedExpression> errors = new ArrayList<>();
        JsonObject out = merge(env("{ \"Mana\": 40, \"Head\": 0.25 }"), errors,
                "{}",
                "{ \"Mana?\": [ { \"Amount#\": \"$Mana * $Head\", \"CalculationType\": \"Additive\" } ] }");
        assertTrue(errors.isEmpty());
        JsonObject el = out.getAsJsonArray("Mana").get(0).getAsJsonObject();
        assertFalse(el.has("Amount#"));
        assertEquals(10, el.get("Amount").getAsInt());
        assertEquals("Additive", el.get("CalculationType").getAsString());
    }

    @Test
    void wholeNumberEmitsInteger() {
        JsonObject out = merge(env("{ \"X\": 4 }"), new ArrayList<>(), "{}", "{ \"V#\": \"$X * 2\" }");
        assertEquals("8", out.get("V").toString());
    }

    @Test
    void fractionalEmitsDecimal() {
        JsonObject out = merge(env("{ \"X\": 17 }"), new ArrayList<>(), "{}", "{ \"V#\": \"$X / 2\" }");
        assertEquals(8.5, out.get("V").getAsDouble());
    }

    @Test
    void higherPriorityLiteralWinsAtMergeTime() {
        List<UnresolvedExpression> errors = new ArrayList<>();
        MergeTable table = JsonDeepMerge.activeTable().with(new ComputeOperator(env("{ \"Mana\": 5 }"), errors));
        JsonObject step1 = JsonDeepMerge.merge(parse("{}"), parse("{ \"Amount#\": \"$Mana\" }"), table);
        JsonObject step2 = JsonDeepMerge.merge(step1, parse("{ \"Amount\": 99 }"), table);
        assertEquals(99, step2.get("Amount").getAsInt());
    }

    @Test
    void failureLeavesKeyAndRecordsError() {
        List<UnresolvedExpression> errors = new ArrayList<>();
        JsonObject out = merge(env("{ \"Mana\": 5 }"), errors, "{}", "{ \"Amount#\": \"$Nope * 2\" }");
        assertEquals(1, errors.size());
        assertTrue(out.has("Amount#"));
        assertFalse(out.has("Amount"));
    }
}
