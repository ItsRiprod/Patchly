package com.riprod.patchly.core.compile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.riprod.patchly.core.JsonDeepMerge;
import com.riprod.patchly.core.MergeTable;
import com.riprod.patchly.core.MetaKeys;
import com.riprod.patchly.core.directive.PatchContext;
import com.riprod.patchly.core.directive.RootDirective;
import com.riprod.patchly.core.vars.ComputeOperator;
import com.riprod.patchly.core.vars.VarEnv;
import com.riprod.patchly.core.vars.VarEnvBuilder;
import com.riprod.patchly.source.BasePolicy;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        List<CompileResult.GatedSource> gated = new ArrayList<>();
        List<Scope> scopes = resolveScopes(sources, roots, ctx, gated);

        List<Ordered> ordered = new ArrayList<>(sources.size());
        for (PatchSource s : sources) {
            if (s.kind().basePolicy() == BasePolicy.SCOPE) continue;

            Scope blocking = s.kind().basePolicy() == BasePolicy.ENVIRONMENT ? null : blockingScope(scopes, s);
            if (blocking != null) {
                gated.add(new CompileResult.GatedSource(s.id(), s.targetRelative(),
                        blocking.directive, blocking.condition, blocking.source.id()));
                continue;
            }

            Gate gate = evaluate(s.patchJson(), roots, ctx);
            if (!gate.keep) {
                gated.add(new CompileResult.GatedSource(
                        s.id(), s.targetRelative(), gate.directive, gate.condition));
                continue;
            }
            ordered.add(new Ordered(s, gate.order));
        }
        ordered.sort(ORDER);

        List<CompileResult.UnresolvedExpression> expressions = new ArrayList<>();
        List<PatchSource> envSources = new ArrayList<>();
        for (Ordered o : ordered) {
            if (o.source.kind().basePolicy() == BasePolicy.ENVIRONMENT) envSources.add(o.source);
        }
        VarEnv env = VarEnvBuilder.build(envSources, expressions);
        MergeTable effective = table.with(new ComputeOperator(env, expressions));

        Map<String, List<JsonObject>> putsByTarget = new HashMap<>();
        Map<String, JsonObject> seeds = new HashMap<>();
        for (Ordered o : ordered) {
            if (o.source.kind().basePolicy() != BasePolicy.OPTIONAL) continue;
            putsByTarget.computeIfAbsent(o.source.targetRelative(), k -> new ArrayList<>())
                    .add(o.source.patchJson());
            seeds.computeIfAbsent(o.source.targetRelative(), k -> o.source.kind().seedWhenAbsent());
        }

        List<Ordered> merges = withScopeContributions(ordered, scopes);

        List<CompileResult.UnresolvedImport> unresolved = new ArrayList<>();
        ImportResolverImpl imports = new ImportResolverImpl(assetIndex, baseResolver, putsByTarget, effective, ctx, unresolved);
        Set<String> markers = effective.directives().markerKeys();

        Map<String, JsonObject> outputs = new LinkedHashMap<>();
        Map<Path, String> sourceToTarget = new LinkedHashMap<>();
        Map<String, List<CompileResult.Contribution>> contributions = new LinkedHashMap<>();
        List<CompileResult.MissingBase> missing = new ArrayList<>();

        for (Ordered o : merges) {
            PatchSource s = o.source;
            BasePolicy policy = s.kind().basePolicy();
            if (policy == BasePolicy.ENVIRONMENT) continue;
            String target = s.targetRelative();

            JsonObject accumulator = outputs.get(target);
            if (accumulator == null) {
                JsonObject base = baseResolver.resolveBase(target);
                if (base == null) {
                    if (policy == BasePolicy.REQUIRED) {
                        missing.add(new CompileResult.MissingBase(s.id(), target));
                        continue;
                    }
                    base = policy == BasePolicy.SCOPE ? seeds.get(target) : s.kind().seedWhenAbsent();
                    if (base == null) continue;
                }
                accumulator = base;
            }

            JsonObject merged = JsonDeepMerge.merge(accumulator, s.patchJson(), effective, ctx, imports, target);
            JsonDeepMerge.stripMergeKey(merged);
            MetaKeys.stripMarkersDeep(merged, markers);
            outputs.put(target, merged);
            if (policy != BasePolicy.SCOPE) sourceToTarget.put(s.id(), target);
            contributions.computeIfAbsent(target, k -> new ArrayList<>())
                    .add(new CompileResult.Contribution(s.id(), s.kind().extension(), o.priority));
        }

        return new CompileResult(outputs, sourceToTarget, missing, unresolved, expressions, gated, contributions);
    }

    @Nonnull
    private static List<Scope> resolveScopes(@Nonnull List<PatchSource> sources,
            @Nonnull List<RootDirective> roots,
            @Nonnull PatchContext ctx,
            @Nonnull List<CompileResult.GatedSource> gated) {
        List<PatchSource> declared = new ArrayList<>();
        for (PatchSource s : sources) {
            if (s.kind().basePolicy() == BasePolicy.SCOPE) declared.add(s);
        }
        declared.sort(Comparator.comparingInt((PatchSource s) -> s.id().getNameCount())
                .thenComparingInt(PatchSource::loadIndex));

        List<Scope> active = new ArrayList<>();
        List<Scope> blocked = new ArrayList<>();
        for (PatchSource s : declared) {
            Scope outer = blockingScope(blocked, s);
            if (outer != null) {
                gated.add(new CompileResult.GatedSource(s.id(), s.targetRelative(),
                        outer.directive, outer.condition, outer.source.id()));
                blocked.add(new Scope(s, outer.directive, outer.condition, 0, true));
                continue;
            }
            Gate gate = evaluate(s.patchJson(), roots, ctx);
            if (gate.keep) {
                active.add(new Scope(s, gate.directive, gate.condition, gate.order, false));
            } else {
                gated.add(new CompileResult.GatedSource(
                        s.id(), s.targetRelative(), gate.directive, gate.condition));
                blocked.add(new Scope(s, gate.directive, gate.condition, gate.order, true));
            }
        }
        active.addAll(blocked);
        return active;
    }

    @Nonnull
    private static List<Ordered> withScopeContributions(@Nonnull List<Ordered> ordered,
            @Nonnull List<Scope> scopes) {
        if (scopes.isEmpty()) return ordered;

        Map<String, Set<Scope>> byTarget = new LinkedHashMap<>();
        for (Ordered o : ordered) {
            BasePolicy policy = o.source.kind().basePolicy();
            if (policy == BasePolicy.ENVIRONMENT) continue;
            Set<Scope> hits = byTarget.computeIfAbsent(o.source.targetRelative(), k -> new LinkedHashSet<>());
            for (Scope scope : scopes) {
                if (!scope.blocking && scope.covers(o.source.id())) hits.add(scope);
            }
        }

        List<Ordered> contributions = new ArrayList<>();
        for (Map.Entry<String, Set<Scope>> entry : byTarget.entrySet()) {
            for (Scope scope : entry.getValue()) {
                contributions.add(new Ordered(scope.contributionTo(entry.getKey()), scope.priority));
            }
        }
        if (contributions.isEmpty()) return ordered;

        List<Ordered> combined = new ArrayList<>(contributions.size() + ordered.size());
        combined.addAll(contributions);
        combined.addAll(ordered);
        combined.sort(ORDER);
        return combined;
    }

    @Nullable
    private static Scope blockingScope(@Nonnull List<Scope> scopes, @Nonnull PatchSource source) {
        for (Scope scope : scopes) {
            if (scope.blocking && scope.covers(source.id())) return scope;
        }
        return null;
    }

    @Nonnull
    private static Gate evaluate(@Nonnull JsonObject patch,
            @Nonnull List<RootDirective> roots,
            @Nonnull PatchContext ctx) {
        int order = 0;
        for (RootDirective rd : roots) {
            JsonElement value = patch.get(rd.markerKey());
            if (value == null) continue;
            if (!rd.keep(value, ctx)) {
                return new Gate(false, order, rd.markerKey(), value.toString());
            }
            order += rd.order(value);
        }
        return new Gate(true, order, "", "");
    }

    private static final Comparator<Ordered> ORDER =
            Comparator.comparingInt((Ordered o) -> o.priority)
                    .thenComparingInt(o -> o.source.loadIndex());

    private record Ordered(PatchSource source, int priority) {
    }

    private record Gate(boolean keep, int order, String directive, String condition) {
    }

    private static final class Scope {
        private final PatchSource source;
        private final Path root;
        private final String directive;
        private final String condition;
        private final int priority;
        private final boolean blocking;

        private Scope(PatchSource source, String directive, String condition, int priority, boolean blocking) {
            this.source = source;
            this.root = source.id().getParent();
            this.directive = directive;
            this.condition = condition;
            this.priority = priority;
            this.blocking = blocking;
        }

        private boolean covers(@Nonnull Path candidate) {
            return root == null || candidate.startsWith(root);
        }

        @Nonnull
        private PatchSource contributionTo(@Nonnull String target) {
            return new PatchSource(source.id(), source.loadIndex(), target, source.kind(), source.patchJson());
        }
    }
}
