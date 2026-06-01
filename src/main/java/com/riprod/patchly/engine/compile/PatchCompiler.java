package com.riprod.patchly.engine.compile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.riprod.patchly.engine.JsonDeepMerge;
import com.riprod.patchly.engine.MergeTable;
import com.riprod.patchly.engine.MetaKeys;
import com.riprod.patchly.engine.directive.PatchContext;
import com.riprod.patchly.engine.directive.RootDirective;
import com.riprod.patchly.source.BasePolicy;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PatchCompiler {
    @Nonnull
    public CompileResult compile(@Nonnull List<PatchSource> sources,
            @Nonnull BaseResolver baseResolver,
            @Nonnull PatchContext ctx,
            @Nonnull MergeTable table) {
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
            JsonObject stripped = patch.deepCopy();
            MetaKeys.strip(stripped);
            ordered.add(new Ordered(s, order, stripped));
        }
        ordered.sort(Comparator.comparingInt((Ordered o) -> o.priority)
                .thenComparingInt(o -> o.source.loadIndex()));

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

            JsonObject merged = JsonDeepMerge.merge(accumulator, o.strippedPatch, table, ctx);
            JsonDeepMerge.stripMergeKey(merged);
            outputs.put(target, merged);
            sourceToTarget.put(s.id(), target);
        }

        return new CompileResult(outputs, sourceToTarget, missing);
    }

    private record Ordered(PatchSource source, int priority, JsonObject strippedPatch) {
    }
}
