package com.riprod.patchly.registry;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class Registry<T, S> {
    private final Function<T, String> keyFn;
    private final Function<Collection<T>, S> snapshotFn;
    private final KeyValidator validator;
    private final Map<String, T> members = new LinkedHashMap<>();
    private volatile S cached;

    public Registry(@Nonnull Function<T, String> keyFn,
                    @Nonnull Function<Collection<T>, S> snapshotFn,
                    @Nonnull KeyValidator validator) {
        this.keyFn = keyFn;
        this.snapshotFn = snapshotFn;
        this.validator = validator;
    }

    public synchronized void register(@Nonnull T member) {
        String key = keyFn.apply(member);
        validator.validate(key);
        members.put(key, member);
        cached = null;
    }

    @Nonnull
    public S snapshot() {
        S local = cached;
        if (local == null) {
            synchronized (this) {
                if (cached == null) {
                    cached = snapshotFn.apply(new ArrayList<>(members.values()));
                }
                local = cached;
            }
        }
        return local;
    }

    @Nonnull
    @SafeVarargs
    public final S isolatedSnapshot(@Nonnull T... isolatedMembers) {
        List<T> list = new ArrayList<>(isolatedMembers.length);
        for (T m : isolatedMembers) {
            validator.validate(keyFn.apply(m));
            list.add(m);
        }
        return snapshotFn.apply(list);
    }
}
