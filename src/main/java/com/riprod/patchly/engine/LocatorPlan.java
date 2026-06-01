package com.riprod.patchly.engine;

import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import java.util.List;

public record LocatorPlan(@Nonnull JsonObject cleanPayload, @Nonnull List<Integer> targetIndices) {
    public boolean matched() {
        return !targetIndices.isEmpty();
    }
}
