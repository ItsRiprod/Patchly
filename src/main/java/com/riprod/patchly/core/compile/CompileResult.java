package com.riprod.patchly.core.compile;

import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record CompileResult(@Nonnull Map<String, JsonObject> outputs,
        @Nonnull Map<Path, String> sourceToTarget,
        @Nonnull List<MissingBase> missingBases,
        @Nonnull List<UnresolvedImport> unresolvedImports,
        @Nonnull List<UnresolvedExpression> unresolvedExpressions) {

    public record MissingBase(@Nonnull Path source, @Nonnull String target) {
    }

    public record UnresolvedImport(@Nonnull String fromTarget, @Nonnull String ref) {
    }

    public record UnresolvedExpression(@Nonnull String where, @Nonnull String expression,
            @Nonnull String reason) {
    }
}
