package com.riprod.patchly.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;

public final class PathUtil {
    public static final String JSON_EXTENSION = ".json";

    private PathUtil() {}

    public static boolean hasExtension(@Nonnull Path path, @Nonnull String extension) {
        Path fileName = path.getFileName();
        if (fileName == null) return false;
        return fileName.toString().endsWith(extension);
    }

    public static boolean isJsonFile(@Nonnull Path path) {
        return hasExtension(path, JSON_EXTENSION);
    }

    @Nonnull
    public static String normalizeRelative(@Nonnull Path packRoot, @Nonnull Path file) {
        return packRoot.relativize(file).toString().replace('\\', '/');
    }

    @Nullable
    public static String stripSuffix(@Nonnull String value, @Nonnull String suffix) {
        if (!value.endsWith(suffix)) return null;
        return value.substring(0, value.length() - suffix.length());
    }

    @Nonnull
    public static String recoverTargetExtension(@Nonnull String stemWithoutOperation) {
        int lastDot = stemWithoutOperation.lastIndexOf('.');
        int lastSlash = stemWithoutOperation.lastIndexOf('/');
        if (lastDot > lastSlash + 1) return stemWithoutOperation;
        return stemWithoutOperation + JSON_EXTENSION;
    }
}
