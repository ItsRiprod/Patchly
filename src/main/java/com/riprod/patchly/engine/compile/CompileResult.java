package com.riprod.patchly.engine.compile;

import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record CompileResult(@Nonnull Map<String, JsonObject> outputs,
        @Nonnull Map<Path, String> sourceToTarget,
        @Nonnull List<MissingBase> missingBases) {

    public record MissingBase(@Nonnull Path source, @Nonnull String target) {
    }
}
