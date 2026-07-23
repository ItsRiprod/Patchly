package com.riprod.patchly.core;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class OperatorTable {
    private final List<MergeOperator> bySuffixDesc;
    private final MergeOperator defaultOperator;

    public OperatorTable(@Nonnull Collection<MergeOperator> operators) {
        List<MergeOperator> nonDefault = new ArrayList<>();
        MergeOperator found = null;
        for (MergeOperator op : operators) {
            if (op.suffix().isEmpty()) {
                found = op;
            } else {
                nonDefault.add(op);
            }
        }
        nonDefault.sort(Comparator.comparingInt((MergeOperator o) -> o.suffix().length()).reversed());
        this.bySuffixDesc = List.copyOf(nonDefault);
        if (found == null) {
            throw new IllegalStateException("no default (empty-suffix) operator registered");
        }
        this.defaultOperator = found;
    }

    @Nonnull
    public MergeOperator forKey(@Nonnull String key) {
        for (MergeOperator op : bySuffixDesc) {
            String s = op.suffix();
            if (key.length() > s.length() && key.endsWith(s)) return op;
        }
        return defaultOperator;
    }

    @Nonnull
    public String baseKey(@Nonnull String key, @Nonnull MergeOperator operator) {
        return key.substring(0, key.length() - operator.suffix().length());
    }

    @Nonnull
    public OperatorTable with(@Nonnull MergeOperator... extra) {
        List<MergeOperator> all = new ArrayList<>(bySuffixDesc);
        all.add(defaultOperator);
        Collections.addAll(all, extra);
        return new OperatorTable(all);
    }
}
