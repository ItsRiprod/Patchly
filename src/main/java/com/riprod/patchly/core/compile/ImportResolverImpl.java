package com.riprod.patchly.core.compile;

import com.google.gson.JsonObject;
import com.riprod.patchly.core.ImportResolver;
import com.riprod.patchly.core.JsonDeepMerge;
import com.riprod.patchly.core.MergeTable;
import com.riprod.patchly.core.directive.PatchContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ImportResolverImpl implements ImportResolver {
    private final AssetIndex index;
    private final BaseResolver baseResolver;
    private final Map<String, List<JsonObject>> putsByTarget;
    private final MergeTable table;
    private final PatchContext ctx;
    private final List<CompileResult.UnresolvedImport> unresolved;
    private final Map<String, JsonObject> snapshots = new HashMap<>();
    private final Deque<String> active = new ArrayDeque<>();

    public ImportResolverImpl(@Nullable AssetIndex index, @Nonnull BaseResolver baseResolver,
            @Nonnull Map<String, List<JsonObject>> putsByTarget, @Nonnull MergeTable table,
            @Nonnull PatchContext ctx, @Nonnull List<CompileResult.UnresolvedImport> unresolved) {
        this.index = index;
        this.baseResolver = baseResolver;
        this.putsByTarget = putsByTarget;
        this.table = table;
        this.ctx = ctx;
        this.unresolved = unresolved;
    }

    @Nullable
    @Override
    public JsonObject resolve(@Nonnull String fromTarget, @Nonnull String ref) {
        String target = index == null ? null : index.resolveRef(fromTarget, ref);
        if (target == null) {
            unresolved.add(new CompileResult.UnresolvedImport(fromTarget, ref));
            return null;
        }
        return snapshot(target);
    }

    @Nullable
    private JsonObject snapshot(@Nonnull String target) {
        JsonObject cached = snapshots.get(target);
        if (cached != null) return cached;
        if (active.contains(target)) return null;
        active.push(target);
        JsonObject acc;
        try {
            BaseResolver.ResolvedBase base = baseResolver.resolveBase(target);
            acc = base != null ? base.json().deepCopy() : new JsonObject();
            for (JsonObject put : putsByTarget.getOrDefault(target, List.of())) {
                acc = JsonDeepMerge.merge(acc, put, table, ctx, this, target);
            }
        } finally {
            active.pop();
        }
        snapshots.put(target, acc);
        return acc;
    }
}
