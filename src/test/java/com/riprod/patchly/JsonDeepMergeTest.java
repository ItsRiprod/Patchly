package com.riprod.patchly;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.riprod.patchly.core.JsonDeepMerge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonDeepMergeTest {

    private static JsonObject merge(String base, String patch) {
        return JsonDeepMerge.merge(
                JsonParser.parseString(base).getAsJsonObject(),
                JsonParser.parseString(patch).getAsJsonObject());
    }

    private static JsonArray arr(JsonObject o, String key) {
        return o.getAsJsonArray(key);
    }

    // --- existing behavior (regression) ---

    @Test
    void bareArrayReplacesWholesaleWhenNoMatch() {
        JsonObject out = merge(
                "{ \"Items\": [ { \"Id\": \"x\", \"keep\": 1 } ] }",
                "{ \"Items\": [ { \"Id\": \"x\" } ] }");
        // no implicit Id matching: the whole array is replaced, "keep" is gone
        assertEquals(1, arr(out, "Items").size());
        assertFalse(arr(out, "Items").get(0).getAsJsonObject().has("keep"));
    }

    @Test
    void plusAppends() {
        JsonObject out = merge("{ \"a\": [1, 2] }", "{ \"a+\": [3] }");
        assertEquals(3, arr(out, "a").size());
        assertEquals(3, arr(out, "a").get(2).getAsInt());
    }

    @Test
    void nullDeletesKey() {
        JsonObject out = merge("{ \"a\": 1, \"b\": 2 }", "{ \"b\": null }");
        assertTrue(out.has("a"));
        assertFalse(out.has("b"));
    }

    // --- positional ~ ---

    @Test
    void positionalExtendsAtIndex() {
        JsonObject out = merge(
                "{ \"Items\": [ { \"a\": 1 }, { \"b\": 2 } ] }",
                "{ \"Items~\": [ { \"a\": 9 } ] }");
        assertEquals(9, arr(out, "Items").get(0).getAsJsonObject().get("a").getAsInt());
        assertEquals(2, arr(out, "Items").get(1).getAsJsonObject().get("b").getAsInt());
    }

    @Test
    void emptyObjectIsAnEmergentNoOp() {
        // {} is not a special-cased skip token: merging an empty object changes nothing
        JsonObject out = merge(
                "{ \"Items\": [ { \"a\": 1 }, { \"b\": 2 } ] }",
                "{ \"Items~\": [ {}, { \"b\": 9 } ] }");
        assertEquals(1, arr(out, "Items").get(0).getAsJsonObject().get("a").getAsInt());
        assertEquals(9, arr(out, "Items").get(1).getAsJsonObject().get("b").getAsInt());
    }

    // --- $Match locator ---

    @Test
    void matchHitExtendsInPlace() {
        JsonObject out = merge(
                "{ \"Items\": [ { \"Id\": \"x\", \"n\": 1 }, { \"Id\": \"y\" } ] }",
                "{ \"Items~\": [ { \"$Match\": \"Id\", \"Id\": \"y\", \"n\": 5 } ] }");
        assertEquals(2, arr(out, "Items").size());
        assertEquals(5, arr(out, "Items").get(1).getAsJsonObject().get("n").getAsInt());
        assertEquals(1, arr(out, "Items").get(0).getAsJsonObject().get("n").getAsInt());
    }

    @Test
    void nestedAppendInsideMatchedElement() {
        // the real Tools use case: locate by Id, append a SubCategory
        JsonObject out = merge(
                "{ \"Children\": [ { \"Id\": \"Tools\", \"Name\": \"t\" }, { \"Id\": \"Weapons\" } ] }",
                "{ \"Children~\": [ { \"$Match\": \"Id\", \"Id\": \"Tools\", \"SubCategories+\": [ { \"Id\": \"Staffs\" } ] } ] }");
        JsonObject tools = arr(out, "Children").get(0).getAsJsonObject();
        assertEquals("Tools", tools.get("Id").getAsString());
        assertEquals("t", tools.get("Name").getAsString());
        assertEquals(1, tools.getAsJsonArray("SubCategories").size());
        assertEquals("Staffs", tools.getAsJsonArray("SubCategories").get(0).getAsJsonObject().get("Id").getAsString());
        // sibling untouched
        assertFalse(arr(out, "Children").get(1).getAsJsonObject().has("SubCategories"));
    }

    @Test
    void matchStrippedFromOutput() {
        JsonObject out = merge(
                "{ \"Items\": [ { \"Id\": \"x\" } ] }",
                "{ \"Items~\": [ { \"$Match\": \"Id\", \"Id\": \"x\", \"n\": 1 } ] }");
        assertFalse(out.toString().contains("$Match"));
    }

    @Test
    void matchAppliesToAllMatches() {
        JsonObject out = merge(
                "{ \"Items\": [ { \"Id\": \"x\", \"n\": 1 }, { \"Id\": \"x\", \"n\": 1 } ] }",
                "{ \"Items~\": [ { \"$Match\": \"Id\", \"Id\": \"x\", \"n\": 9 } ] }");
        assertEquals(9, arr(out, "Items").get(0).getAsJsonObject().get("n").getAsInt());
        assertEquals(9, arr(out, "Items").get(1).getAsJsonObject().get("n").getAsInt());
    }

    @Test
    void customMatchField() {
        JsonObject out = merge(
                "{ \"Items\": [ { \"Name\": \"Sword\" } ] }",
                "{ \"Items~\": [ { \"$Match\": \"Name\", \"Name\": \"Sword\", \"Tier\": 3 } ] }");
        assertEquals(3, arr(out, "Items").get(0).getAsJsonObject().get("Tier").getAsInt());
    }

    // --- $Match miss: fallback determined by host suffix ---

    @Test
    void matchMissUnderAppendAppends() {
        JsonObject out = merge(
                "{ \"Items\": [ { \"Id\": \"x\" } ] }",
                "{ \"Items+\": [ { \"$Match\": \"Id\", \"Id\": \"new\", \"v\": 5 } ] }");
        assertEquals(2, arr(out, "Items").size());
        assertEquals("new", arr(out, "Items").get(1).getAsJsonObject().get("Id").getAsString());
        assertFalse(out.toString().contains("$Match"));
    }

    @Test
    void matchMissUnderBareIsNoOp() {
        JsonObject out = merge(
                "{ \"Items\": [ { \"Id\": \"x\" } ] }",
                "{ \"Items\": [ { \"$Match\": \"Id\", \"Id\": \"none\", \"v\": 5 } ] }");
        assertEquals(1, arr(out, "Items").size());
        assertEquals("x", arr(out, "Items").get(0).getAsJsonObject().get("Id").getAsString());
    }

    @Test
    void matchMissUnderPositionalFallsBackToIndex() {
        JsonObject out = merge(
                "{ \"Items\": [ { \"Id\": \"x\", \"v\": 1 } ] }",
                "{ \"Items~\": [ { \"$Match\": \"Id\", \"Id\": \"zzz\", \"v\": 2 } ] }");
        // no element with Id=zzz, so positional fallback merges into index 0
        assertEquals(1, arr(out, "Items").size());
        assertEquals(2, arr(out, "Items").get(0).getAsJsonObject().get("v").getAsInt());
    }

    // --- fill-if-absent ? ---

    @Test
    void fillAddsKeyWhenAbsent() {
        JsonObject out = merge("{ \"a\": 1 }", "{ \"b?\": 2 }");
        assertEquals(1, out.get("a").getAsInt());
        assertEquals(2, out.get("b").getAsInt());
    }

    @Test
    void fillLeavesExistingScalarUntouched() {
        JsonObject out = merge("{ \"b\": 1 }", "{ \"b?\": 2 }");
        assertEquals(1, out.get("b").getAsInt());
    }

    @Test
    void fillLeavesExistingArrayUntouched() {
        JsonObject out = merge("{ \"Mana\": [ { \"Amount\": 5 } ] }", "{ \"Mana?\": [ { \"Amount\": 200 } ] }");
        assertEquals(1, arr(out, "Mana").size());
        assertEquals(5, arr(out, "Mana").get(0).getAsJsonObject().get("Amount").getAsInt());
    }

    @Test
    void fillAddsArrayWhenAbsent() {
        JsonObject out = merge("{}", "{ \"Mana?\": [ { \"Amount\": 200 } ] }");
        assertEquals(1, arr(out, "Mana").size());
        assertEquals(200, arr(out, "Mana").get(0).getAsJsonObject().get("Amount").getAsInt());
    }

    @Test
    void fillLeavesExistingObjectUntouched() {
        JsonObject out = merge("{ \"o\": { \"k\": 1 } }", "{ \"o?\": { \"k\": 2, \"extra\": 3 } }");
        assertEquals(1, out.getAsJsonObject("o").get("k").getAsInt());
        assertFalse(out.getAsJsonObject("o").has("extra"));
    }

    @Test
    void fillResolvesPerKeyInsideNestedObject() {
        JsonObject out = merge(
                "{ \"StatModifiers\": { \"Mana\": [ { \"Amount\": 5 } ] } }",
                "{ \"StatModifiers\": { \"Mana?\": [ { \"Amount\": 200 } ], \"Volatility?\": [ { \"Amount\": 26 } ] } }");
        JsonObject mods = out.getAsJsonObject("StatModifiers");
        // Mana already present -> base kept
        assertEquals(5, mods.getAsJsonArray("Mana").get(0).getAsJsonObject().get("Amount").getAsInt());
        // Volatility absent -> filled
        assertEquals(26, mods.getAsJsonArray("Volatility").get(0).getAsJsonObject().get("Amount").getAsInt());
    }

    // --- prepend - ---

    @Test
    void prependAddsBeforeExisting() {
        JsonObject out = merge("{ \"a\": [1, 2] }", "{ \"a-\": [3] }");
        assertEquals(3, arr(out, "a").size());
        assertEquals(3, arr(out, "a").get(0).getAsInt());
        assertEquals(1, arr(out, "a").get(1).getAsInt());
        assertEquals(2, arr(out, "a").get(2).getAsInt());
    }

    @Test
    void prependPreservesPatchOrder() {
        JsonObject out = merge("{ \"a\": [1] }", "{ \"a-\": [2, 3] }");
        assertEquals(2, arr(out, "a").get(0).getAsInt());
        assertEquals(3, arr(out, "a").get(1).getAsInt());
        assertEquals(1, arr(out, "a").get(2).getAsInt());
    }

    @Test
    void prependCreatesWhenAbsent() {
        JsonObject out = merge("{}", "{ \"a-\": [1] }");
        assertEquals(1, arr(out, "a").size());
        assertEquals(1, arr(out, "a").get(0).getAsInt());
    }

    @Test
    void prependNonArrayIgnored() {
        JsonObject out = merge("{ \"a\": [1] }", "{ \"a-\": 5 }");
        assertEquals(1, arr(out, "a").size());
        assertEquals(1, arr(out, "a").get(0).getAsInt());
    }

    @Test
    void prependMatchHitMergesInPlace() {
        JsonObject out = merge(
                "{ \"Items\": [ { \"Id\": \"x\", \"v\": 1 } ] }",
                "{ \"Items-\": [ { \"$Match\": \"Id\", \"Id\": \"x\", \"v\": 9 } ] }");
        // hit merges into the matched base element, no new element prepended
        assertEquals(1, arr(out, "Items").size());
        assertEquals(9, arr(out, "Items").get(0).getAsJsonObject().get("v").getAsInt());
        assertFalse(arr(out, "Items").get(0).getAsJsonObject().has("$Match"));
    }

    @Test
    void prependMatchMissPrepends() {
        JsonObject out = merge(
                "{ \"Items\": [ { \"Id\": \"x\" } ] }",
                "{ \"Items-\": [ { \"$Match\": \"Id\", \"Id\": \"new\", \"n\": 1 } ] }");
        assertEquals(2, arr(out, "Items").size());
        assertEquals("new", arr(out, "Items").get(0).getAsJsonObject().get("Id").getAsString());
        assertEquals("x", arr(out, "Items").get(1).getAsJsonObject().get("Id").getAsString());
        assertFalse(arr(out, "Items").get(0).getAsJsonObject().has("$Match"));
    }
}
