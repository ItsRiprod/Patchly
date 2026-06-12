package com.riprod.patchly.core.directive.builtins;

import com.google.gson.JsonElement;
import com.riprod.patchly.core.directive.PatchContext;
import com.riprod.patchly.core.directive.RootDirective;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public final class RequiresDirective implements RootDirective {
    private static final String MARKER = "$Requires";

    @Nonnull
    @Override
    public String markerKey() {
        return MARKER;
    }

    @Override
    public boolean keep(@Nonnull JsonElement markerValue, @Nonnull PatchContext ctx) {
        List<String> required = new ArrayList<>();
        if (markerValue.isJsonArray()) {
            for (JsonElement e : markerValue.getAsJsonArray()) {
                if (e.isJsonPrimitive()) required.add(e.getAsString());
            }
        } else if (markerValue.isJsonPrimitive()) {
            required.add(markerValue.getAsString());
        }

        for (String entry : required) {
            // pack identifiers are exactly Group:Name (one colon); a second colon starts the
            // optional semver range, which itself never contains a colon
            int secondColon = entry.indexOf(':', entry.indexOf(':') + 1);
            String name = secondColon >= 0 ? entry.substring(0, secondColon) : entry;
            String range = secondColon >= 0 ? entry.substring(secondColon + 1) : null;

            if (!ctx.packPresent(name)) return false;
            if (range != null && !ctx.versionSatisfies(name, range)) return false;
        }
        return true;
    }
}
