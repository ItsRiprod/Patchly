package com.riprod.patchly.registry;

import javax.annotation.Nonnull;

@FunctionalInterface
public interface KeyValidator {
    KeyValidator NONE = key -> {};

    void validate(@Nonnull String key);

    @Nonnull
    static KeyValidator requirePrefix(@Nonnull String prefix) {
        return key -> {
            if (!key.startsWith(prefix)) {
                throw new IllegalArgumentException("key '" + key + "' must start with '" + prefix + "'");
            }
        };
    }
}
