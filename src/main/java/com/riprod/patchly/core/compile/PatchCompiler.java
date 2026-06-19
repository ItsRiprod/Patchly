package com.riprod.patchly.core.compile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.riprod.patchly.core.JsonDeepMerge;
import com.riprod.patchly.core.MergeTable;
import com.riprod.patchly.core.MetaKeys;
import com.riprod.patchly.core.directive.ObjectDirective;
import com.riprod.patchly.core.directive.PatchContext;
import com.riprod.patchly.core.directive.RootDirective;
import com.riprod.patchly.source.BasePolicy;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class PatchCompiler {
    @Nonnull
    public CompileResult compile(@Nonnull List<PatchSource> sources,
            @Nonnull BaseResolver baseResolver,
            @Nonnull PatchContext ctx,
            @Nonnull MergeTable table) {
        return compile(sources, baseResolver, ctx, table, null);
    }

    @Nonnull
    public CompileResult compile(@Nonnull List<PatchSource> sources,
            @Nonnull BaseResolver baseResolver,
            @Nonnull PatchContext ctx,
            @Nonnull MergeTable table,
            @Nullable AssetIndex assetIndex) {
        List<RootDirective> roots = table.directives().rootDirectives();

        List<Ordered> ordered = new ArrayList<>(sources.size());
        for (PatchSource s : sources) {
            JsonObject patch = s.patchJson();
            boolean keep = true;
            int order = 0;
            for (RootDirective rd : roots) {
                JsonElement value = patch.get(rd.markerKey());
                if (value == null)
                    continue;
                if (!rd.keep(value, ctx)) {
                    keep = false;
                    break;
                }
                order += rd.order(value);
            }
            if (!keep)
                continue;
            ordered.add(new Ordered(s, order));
        }
        ordered.sort(Comparator.comparingInt((Ordered o) -> o.priority)
                .thenComparingInt(o -> o.source.loadIndex()));

        Map<String, List<JsonObject>> putsByTarget = new HashMap<>();
        for (Ordered o : ordered) {
            if (o.source.kind().basePolicy() == BasePolicy.OPTIONAL) {
                putsByTarget.computeIfAbsent(o.source.targetRelative(), k -> new ArrayList<>())
                        .add(o.source.patchJson());
            }
        }

        List<CompileResult.UnresolvedImport> unresolved = new ArrayList<>();
        ImportResolverImpl imports = new ImportResolverImpl(assetIndex, baseResolver, putsByTarget, table, ctx, unresolved);
        Set<String> objectMarkers = table.directives().objectDirectives().stream()
                .map(ObjectDirective::markerKey).collect(Collectors.toSet());

        Map<String, JsonObject> outputs = new LinkedHashMap<>();
        Map<java.nio.file.Path, String> sourceToTarget = new LinkedHashMap<>();
        List<CompileResult.MissingBase> missing = new ArrayList<>();

        for (Ordered o : ordered) {
            PatchSource s = o.source;
            String target = s.targetRelative();

            JsonObject accumulator = outputs.get(target);
            if (accumulator == null) {
                JsonObject base = baseResolver.resolveBase(target);
                if (base == null) {
                    if (s.kind().basePolicy() == BasePolicy.REQUIRED) {
                        missing.add(new CompileResult.MissingBase(s.id(), target));
                        continue;
                    }
                    base = s.kind().seedWhenAbsent();
                }
                accumulator = base;
            }

            JsonObject merged = JsonDeepMerge.merge(accumulator, s.patchJson(), table, ctx, imports, target);
            JsonDeepMerge.stripMergeKey(merged);
            MetaKeys.stripMarkersDeep(merged, objectMarkers);
            outputs.put(target, merged);
            sourceToTarget.put(s.id(), target);
        }

        return new CompileResult(outputs, sourceToTarget, missing, unresolved);
    }

    private record Ordered(PatchSource source, int priority) {
    }
}
