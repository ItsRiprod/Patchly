package com.riprod.patchly;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.riprod.patchly.engine.JsonDeepMerge;
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
}
