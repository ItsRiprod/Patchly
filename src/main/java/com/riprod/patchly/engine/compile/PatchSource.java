package com.riprod.patchly.engine.compile;

import com.google.gson.JsonObject;
import com.riprod.patchly.source.SourceKind;

import javax.annotation.Nonnull;
import java.nio.file.Path;

public record PatchSource(@Nonnull Path id, int loadIndex, @Nonnull String targetRelative,
        @Nonnull SourceKind kind, @Nonnull JsonObject patchJson) {
}
