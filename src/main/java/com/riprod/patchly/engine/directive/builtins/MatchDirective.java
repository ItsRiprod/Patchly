package com.riprod.patchly.engine.directive.builtins;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.riprod.patchly.engine.LocatorPlan;
import com.riprod.patchly.engine.directive.ElementDirective;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public final class MatchDirective implements ElementDirective {
    private static final String MARKER = "$Match";

    @Nonnull
    @Override
    public String markerKey() {
        return MARKER;
    }

    @Nonnull
    @Override
    public LocatorPlan locate(@Nonnull JsonObject element, @Nonnull JsonArray base) {
        JsonObject clean = element.deepCopy();
        clean.remove(MARKER);

        List<Integer> indices = new ArrayList<>();
        JsonElement fieldEl = element.get(MARKER);
        if (fieldEl != null && fieldEl.isJsonPrimitive() && fieldEl.getAsJsonPrimitive().isString()) {
            String field = fieldEl.getAsString();
            JsonElement wanted = clean.get(field);
            if (wanted != null && !wanted.isJsonNull()) {
                for (int i = 0; i < base.size(); i++) {
                    JsonElement b = base.get(i);
                    if (!b.isJsonObject()) continue;
                    JsonElement have = b.getAsJsonObject().get(field);
                    if (have != null && have.equals(wanted)) indices.add(i);
                }
            }
        }
        return new LocatorPlan(clean, indices);
    }
}
