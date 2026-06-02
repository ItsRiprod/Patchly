package com.riprod.patchly.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.riprod.patchly.core.JsonDeepMerge;
import com.riprod.patchly.core.MergeContext;
import com.riprod.patchly.core.MergeOperator;
import com.riprod.patchly.core.MergeTable;
import com.riprod.patchly.core.OperatorRegistry;
import com.riprod.patchly.core.OperatorTable;
import com.riprod.patchly.core.directive.DirectiveRegistry;
import com.riprod.patchly.core.ops.builtin.ReplaceOperator;

import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ExtensibilityTest {

    // a new operator is one class; it composes under the existing $Match locator with zero engine edits
    private static final class RemoveOperator implements MergeOperator {
        @Nonnull
        @Override
        public String suffix() {
            return "-";
        }

        @Override
        public int phase() {
            return 25;
        }

        @Override
        public void apply(@Nonnull JsonObject target, @Nonnull String baseKey,
                          @Nonnull JsonElement patchValue, @Nonnull MergeContext ctx) {
            if (!patchValue.isJsonArray()) return;
            ctx.runArrayMerge(target, baseKey, patchValue.getAsJsonArray(), this);
        }

        @Override
        public void onLocatorHit(@Nonnull JsonArray base, @Nonnull List<Integer> indices,
                                 @Nonnull JsonObject cleanPayload, @Nonnull MergeContext ctx) {
            for (int i = indices.size() - 1; i >= 0; i--) {
                base.remove((int) indices.get(i));
            }
        }

        @Override
        public void onLocatorMiss(@Nonnull JsonArray base, int index,
                                  @Nonnull JsonObject cleanPayload, @Nonnull MergeContext ctx) {
        }

        @Override
        public void onPlainElement(@Nonnull JsonArray base, int index,
                                   @Nonnull JsonElement element, @Nonnull MergeContext ctx) {
        }
    }

    private static MergeTable tableWith(MergeOperator... extra) {
        MergeOperator[] all = new MergeOperator[extra.length + 1];
        all[0] = new ReplaceOperator();
        System.arraycopy(extra, 0, all, 1, extra.length);
        return new MergeTable(OperatorRegistry.isolatedTable(all), DirectiveRegistry.table());
    }

    @Test
    void newOperatorComposesUnderMatchLocator() {
        JsonObject base = JsonParser.parseString(
                "{ \"Items\": [ { \"Id\": \"a\" }, { \"Id\": \"b\" } ] }").getAsJsonObject();
        JsonObject patch = JsonParser.parseString(
                "{ \"Items-\": [ { \"$Match\": \"Id\", \"Id\": \"a\" } ] }").getAsJsonObject();

        JsonObject out = JsonDeepMerge.merge(base, patch, tableWith(new RemoveOperator()));
        JsonArray items = out.getAsJsonArray("Items");
        assertEquals(1, items.size());
        assertEquals("b", items.get(0).getAsJsonObject().get("Id").getAsString());
    }

    @Test
    void longestSuffixWins() {
        OperatorTable table = OperatorRegistry.isolatedTable(
                new ReplaceOperator(), new RemoveOperator());
        // "Foo-" ends with the registered "-" suffix; a bare "-" key has no base and stays literal
        assertSame(table.forKey("Foo-").getClass(), RemoveOperator.class);
        assertEquals("", table.forKey("-").suffix());
    }
}
