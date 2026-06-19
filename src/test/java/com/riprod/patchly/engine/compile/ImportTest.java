package com.riprod.patchly.engine.compile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.riprod.patchly.core.JsonDeepMerge;
import com.riprod.patchly.core.MergeTable;
import com.riprod.patchly.core.compile.AssetIndex;
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

class ImportTest {
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

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static PatchSource source(String id, int loadIndex, String target, SourceKind kind, String json) {
        return new PatchSource(Path.of(id), loadIndex, target, kind, parse(json));
    }

    private static BaseResolver bases(Map<String, String> map) {
        return target -> map.containsKey(target) ? parse(map.get(target)) : null;
    }

    private static AssetIndex index(Map<String, String> refToTarget) {
        return (from, ref) -> refToTarget.get(ref);
    }

    private static CompileResult compile(List<PatchSource> sources, BaseResolver bases, AssetIndex index) {
        return new PatchCompiler().compile(sources, bases, ALL_PRESENT, TABLE, index);
    }

    @Test
    void objectImportOverridesAndFillDefersToImport() {
        CompileResult out = compile(
                List.of(source("a.patch", 0, "Some_Item.json", PATCH,
                        "{ \"$Requires\": \"Riprod:Hexcode\", \"$Import\": \"Template_Base_Item\","
                                + " \"Quality\": \"Legendary\", \"ItemLevel\": 100,"
                                + " \"PlayerAnimationsId?\": \"DynamicAnimation\" }")),
                bases(Map.of(
                        "Some_Item.json", "{ \"Quality\": \"Rare\", \"ItemLevel\": 40, \"Model\": \"M\","
                                + " \"Texture\": \"T\", \"Interactions\": { \"Primary\": \"Root\", \"Secondary\": \"Root\" } }",
                        "Template_Base_Item.json", "{ \"Quality\": \"Common\", \"ItemLevel\": 10,"
                                + " \"PlayerAnimationsId\": \"Spellbook\","
                                + " \"Interactions\": { \"Primary\": \"Custom\", \"Secondary\": \"Custom\" } }")),
                index(Map.of("Template_Base_Item", "Template_Base_Item.json")));

        JsonObject r = out.outputs().get("Some_Item.json");
        assertEquals("Legendary", r.get("Quality").getAsString());
        assertEquals(100, r.get("ItemLevel").getAsInt());
        assertEquals("M", r.get("Model").getAsString());
        assertEquals("T", r.get("Texture").getAsString());
        assertEquals("Spellbook", r.get("PlayerAnimationsId").getAsString());
        JsonObject interactions = r.getAsJsonObject("Interactions");
        assertEquals("Custom", interactions.get("Primary").getAsString());
        assertEquals("Custom", interactions.get("Secondary").getAsString());
        assertFalse(r.toString().contains("$Import"));
    }

    @Test
    void nestedImportsDeeperWinsAndFillDefersToBaseAndImport() {
        CompileResult out = compile(
                List.of(source("a.patch", 0, "original.json", PATCH,
                        "{ \"$Import\": \"SomeAssetA\", \"Interactions\": { \"$Import\": \"SomeAssetB\","
                                + " \"Primary\": \"Default_Primary\", \"Secondary?\": \"Default_Secondary\","
                                + " \"Jump?\": \"Default_Jump\" } }")),
                bases(Map.of(
                        "original.json", "{ \"Interactions\": { \"Primary\": \"Original_Primary\", \"Jump\": \"Original_Jump\" } }",
                        "SomeAssetA.json", "{ \"Interactions\": { \"Primary\": \"A_Primary\", \"Secondary\": \"A_Secondary\", \"Dodge\": \"A_Dodge\" } }",
                        "SomeAssetB.json", "{ \"Interactions\": { \"Dodge\": \"B_Dodge\", \"Interact\": \"B_Interact\" } }")),
                index(Map.of("SomeAssetA", "SomeAssetA.json", "SomeAssetB", "SomeAssetB.json")));

        JsonObject i = out.outputs().get("original.json").getAsJsonObject("Interactions");
        assertEquals("Default_Primary", i.get("Primary").getAsString());
        assertEquals("A_Secondary", i.get("Secondary").getAsString());
        assertEquals("B_Dodge", i.get("Dodge").getAsString());
        assertEquals("B_Interact", i.get("Interact").getAsString());
        assertEquals("Original_Jump", i.get("Jump").getAsString());
        assertFalse(i.has("Interactions"));
    }

    @Test
    void arrayOfImportsLaterWins() {
        CompileResult out = compile(
                List.of(source("a.patch", 0, "Foo.json", PATCH, "{ \"$Import\": [ \"A\", \"B\" ] }")),
                bases(Map.of(
                        "Foo.json", "{}",
                        "A.json", "{ \"x\": 1, \"y\": 1 }",
                        "B.json", "{ \"y\": 2, \"z\": 2 }")),
                index(Map.of("A", "A.json", "B", "B.json")));

        JsonObject r = out.outputs().get("Foo.json");
        assertEquals(1, r.get("x").getAsInt());
        assertEquals(2, r.get("y").getAsInt());
        assertEquals(2, r.get("z").getAsInt());
    }

    @Test
    void chainedImportThroughPutTemplate() {
        CompileResult out = compile(
                List.of(
                        source("tmplA.put", 0, "A.json", PUT, "{ \"$Import\": \"B\", \"a\": 1 }"),
                        source("foo.patch", 1, "Foo.json", PATCH, "{ \"$Import\": \"A\" }")),
                bases(Map.of(
                        "Foo.json", "{}",
                        "B.json", "{ \"b\": 2 }")),
                index(Map.of("A", "A.json", "B", "B.json")));

        JsonObject r = out.outputs().get("Foo.json");
        assertEquals(1, r.get("a").getAsInt());
        assertEquals(2, r.get("b").getAsInt());
    }

    @Test
    void cyclicImportsTerminate() {
        CompileResult out = compile(
                List.of(
                        source("a.put", 0, "A.json", PUT, "{ \"$Import\": \"B\", \"a\": 1 }"),
                        source("b.put", 1, "B.json", PUT, "{ \"$Import\": \"A\", \"b\": 2 }"),
                        source("foo.patch", 2, "Foo.json", PATCH, "{ \"$Import\": \"A\" }")),
                bases(Map.of("Foo.json", "{}")),
                index(Map.of("A", "A.json", "B", "B.json")));

        JsonObject r = out.outputs().get("Foo.json");
        assertEquals(1, r.get("a").getAsInt());
        assertFalse(r.toString().contains("$Import"));
    }

    @Test
    void missingImportIsRecordedAndRestApplies() {
        CompileResult out = compile(
                List.of(source("a.patch", 0, "Foo.json", PATCH, "{ \"$Import\": \"Nope\", \"keep\": 1 }")),
                bases(Map.of("Foo.json", "{ \"orig\": 1 }")),
                index(Map.of()));

        JsonObject r = out.outputs().get("Foo.json");
        assertEquals(1, r.get("orig").getAsInt());
        assertEquals(1, r.get("keep").getAsInt());
        assertEquals(1, out.unresolvedImports().size());
        assertEquals("Nope", out.unresolvedImports().get(0).ref());
    }

    @Test
    void importResolvesPutCreatedTemplate() {
        CompileResult out = compile(
                List.of(
                        source("tmpl.put", 0, "Template.json", PUT, "{ \"shared\": \"v\" }"),
                        source("foo.patch", 1, "Foo.json", PATCH, "{ \"$Import\": \"Template\", \"own\": 1 }")),
                bases(Map.of("Foo.json", "{}")),
                index(Map.of("Template", "Template.json")));

        JsonObject r = out.outputs().get("Foo.json");
        assertEquals("v", r.get("shared").getAsString());
        assertEquals(1, r.get("own").getAsInt());
    }

    @Test
    void importIsTypeScopedToImportingCodec() {
        AssetIndex typeScoped = (from, ref) -> {
            if (from.startsWith("Server/Item/Items/")) return "Server/Item/Items/" + ref + ".json";
            if (from.startsWith("Server/Item/Block/Blocks/")) return "Server/Item/Block/Blocks/" + ref + ".json";
            return null;
        };
        CompileResult out = compile(
                List.of(source("bar.patch", 0, "Server/Item/Items/Bar.json", PATCH, "{ \"$Import\": \"Foo\" }")),
                bases(Map.of(
                        "Server/Item/Items/Bar.json", "{}",
                        "Server/Item/Items/Foo.json", "{ \"type\": \"item\" }",
                        "Server/Item/Block/Blocks/Foo.json", "{ \"type\": \"block\" }")),
                typeScoped);

        assertEquals("item", out.outputs().get("Server/Item/Items/Bar.json").get("type").getAsString());
    }

    @Test
    void unresolvedImportInsideFillObjectDoesNotLeakMarker() {
        CompileResult out = compile(
                List.of(source("a.patch", 0, "Foo.json", PATCH,
                        "{ \"Extra?\": { \"$Import\": \"Nope\", \"a\": 1 } }")),
                bases(Map.of("Foo.json", "{}")),
                index(Map.of()));

        JsonObject extra = out.outputs().get("Foo.json").getAsJsonObject("Extra");
        assertEquals(1, extra.get("a").getAsInt());
        assertFalse(out.outputs().get("Foo.json").toString().contains("$Import"));
    }
}
