package com.riprod.patchly.core.compile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.riprod.patchly.core.JsonDeepMerge;
import com.riprod.patchly.core.MergeTable;
import com.riprod.patchly.core.MetaKeys;
import com.riprod.patchly.core.directive.PatchContext;
import com.riprod.patchly.core.directive.RootDirective;
import com.riprod.patchly.core.directive.builtins.RequiresDirective;
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
    private static final RequiresDirective REQUIRES = new RequiresDirective();

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
        List<CompileResult.UnresolvedExpression> expressions = new ArrayList<>();

        List<PatchSource> globalVars = new ArrayList<>();
        List<PatchSource> scopedVars = new ArrayList<>();
        List<PatchSource> rest = new ArrayList<>(sources.size());
        for (PatchSource s : sources) {
            if (s.kind().basePolicy() != BasePolicy.ENVIRONMENT) rest.add(s);
            else if (VarEnvBuilder.isGlobals(s)) globalVars.add(s);
            else scopedVars.add(s);
        }

        Map<String, Double> globals = VarEnvBuilder.buildGlobals(
                sourcesOf(gateAll(globalVars, roots, ctx, gated, expressions)), expressions);
        PatchContext ctxGlobals = PatchContext.withVars(ctx, new VarEnv(globals, Map.of()).lookup());
        VarEnv env = new VarEnv(globals, VarEnvBuilder.buildScopes(globals,
                sourcesOf(gateAll(scopedVars, roots, ctxGlobals, gated, expressions)), expressions));
        PatchContext ctxFull = PatchContext.withVars(ctx, env.lookup());

        List<Scope> scopes = resolveScopes(rest, roots, ctxFull, gated, expressions);

        List<Ordered> ordered = new ArrayList<>(rest.size());
        for (PatchSource s : rest) {
            if (s.kind().basePolicy() == BasePolicy.SCOPE) continue;

            Scope blocking = blockingScope(scopes, s);
            if (blocking != null) {
                gated.add(new CompileResult.GatedSource(s.id(), s.targetRelative(),
                        blocking.directive, blocking.condition, blocking.source.id()));
                continue;
            }

            Ordered kept = gateOne(s, roots, ctxFull, gated, expressions);
            if (kept != null) ordered.add(kept);
        }
        ordered.sort(ORDER);

        MergeTable effective = table.with(new ComputeOperator(env, expressions));

        Map<String, List<JsonObject>> putsByTarget = new HashMap<>();
        Map<String, JsonObject> seeds = new HashMap<>();
        Map<String, String> seedPath = new HashMap<>();
        for (Ordered o : ordered) {
            if (o.source.kind().basePolicy() != BasePolicy.OPTIONAL) continue;
            putsByTarget.computeIfAbsent(o.source.targetRelative(), k -> new ArrayList<>())
                    .add(o.source.patchJson());
            seeds.computeIfAbsent(o.source.identity(), k -> o.source.kind().seedWhenAbsent());
            seedPath.putIfAbsent(o.source.identity(), o.source.targetRelative());
        }

        List<Ordered> merges = withScopeContributions(ordered, scopes);

        List<CompileResult.UnresolvedImport> unresolved = new ArrayList<>();
        ImportResolverImpl imports = new ImportResolverImpl(assetIndex, baseResolver, putsByTarget, effective, ctxFull, unresolved);
        Set<String> markers = effective.directives().markerKeys();

        Map<String, JsonObject> merged = new LinkedHashMap<>();
        Map<String, String> writePath = new LinkedHashMap<>();
        Map<Path, String> sourceToIdentity = new LinkedHashMap<>();
        Map<String, List<CompileResult.Contribution>> contributions = new LinkedHashMap<>();
        List<CompileResult.MissingBase> missing = new ArrayList<>();

        for (Ordered o : merges) {
            PatchSource s = o.source;
            BasePolicy policy = s.kind().basePolicy();
            if (policy == BasePolicy.ENVIRONMENT) continue;
            String target = s.targetRelative();
            String identity = s.identity();

            JsonObject accumulator = merged.get(identity);
            if (accumulator == null) {
                BaseResolver.ResolvedBase base = baseResolver.resolveBase(target);
                if (base != null) {
                    accumulator = base.json();
                    writePath.putIfAbsent(identity, base.path());
                } else {
                    accumulator = seeds.get(identity);
                    if (accumulator == null) {
                        if (policy == BasePolicy.REQUIRED) {
                            missing.add(new CompileResult.MissingBase(s.id(), target));
                        }
                        continue;
                    }
                    writePath.putIfAbsent(identity, seedPath.get(identity));
                }
            }

            int before = expressions.size();
            JsonObject next = JsonDeepMerge.merge(accumulator, s.patchJson(), effective, ctxFull, imports, target);
            String output = writePath.get(identity);
            for (int i = before; i < expressions.size(); i++) {
                expressions.set(i, expressions.get(i).withTarget(output));
            }
            JsonDeepMerge.stripMergeKey(next);
            MetaKeys.stripMarkersDeep(next, markers);
            merged.put(identity, next);
            if (policy != BasePolicy.SCOPE) sourceToIdentity.put(s.id(), identity);
            contributions.computeIfAbsent(identity, k -> new ArrayList<>())
                    .add(new CompileResult.Contribution(s.id(), s.kind().extension(), o.priority));
        }

        Map<String, JsonObject> outputs = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> e : merged.entrySet()) {
            outputs.put(writePath.get(e.getKey()), e.getValue());
        }
        Map<Path, String> sourceToTarget = new LinkedHashMap<>();
        for (Map.Entry<Path, String> e : sourceToIdentity.entrySet()) {
            sourceToTarget.put(e.getKey(), writePath.get(e.getValue()));
        }
        Map<String, List<CompileResult.Contribution>> byWritePath = new LinkedHashMap<>();
        for (Map.Entry<String, List<CompileResult.Contribution>> e : contributions.entrySet()) {
            byWritePath.put(writePath.get(e.getKey()), e.getValue());
        }

        return new CompileResult(outputs, sourceToTarget, missing, unresolved, expressions, gated, byWritePath,
                env.asMap());
    }

    @Nonnull
    private static List<Ordered> gateAll(@Nonnull List<PatchSource> sources,
            @Nonnull List<RootDirective> roots,
            @Nonnull PatchContext ctx,
            @Nonnull List<CompileResult.GatedSource> gated,
            @Nonnull List<CompileResult.UnresolvedExpression> expressions) {
        List<Ordered> kept = new ArrayList<>(sources.size());
        for (PatchSource s : sources) {
            Ordered o = gateOne(s, roots, ctx, gated, expressions);
            if (o != null) kept.add(o);
        }
        kept.sort(ORDER);
        return kept;
    }

    @Nullable
    private static Ordered gateOne(@Nonnull PatchSource s,
            @Nonnull List<RootDirective> roots,
            @Nonnull PatchContext ctx,
            @Nonnull List<CompileResult.GatedSource> gated,
            @Nonnull List<CompileResult.UnresolvedExpression> expressions) {
        Gate gate = evaluate(s.patchJson(), roots, ctx);
        if (gate.keep) return new Ordered(s, gate.order);
        gated.add(new CompileResult.GatedSource(s.id(), s.targetRelative(), gate.directive, gate.condition));
        diagnoseGate(s, gate, ctx, expressions);
        return null;
    }

    private static void diagnoseGate(@Nonnull PatchSource s, @Nonnull Gate gate, @Nonnull PatchContext ctx,
            @Nonnull List<CompileResult.UnresolvedExpression> expressions) {
        if (!gate.directive.equals(REQUIRES.markerKey())) return;
        JsonElement value = s.patchJson().get(gate.directive);
        if (value == null) return;
        String where = s.id().toString();
        String target = s.kind().basePolicy() == BasePolicy.ENVIRONMENT ? where : s.targetRelative();
        for (RequiresDirective.Diagnostic d : RequiresDirective.diagnose(value, ctx)) {
            expressions.add(new CompileResult.UnresolvedExpression(
                    where, d.literal(), d.reason(), target, d.missingScope()));
        }
    }

    @Nonnull
    private static List<PatchSource> sourcesOf(@Nonnull List<Ordered> ordered) {
        List<PatchSource> out = new ArrayList<>(ordered.size());
        for (Ordered o : ordered) out.add(o.source);
        return out;
    }

    @Nonnull
    private static List<Scope> resolveScopes(@Nonnull List<PatchSource> sources,
            @Nonnull List<RootDirective> roots,
            @Nonnull PatchContext ctx,
            @Nonnull List<CompileResult.GatedSource> gated,
            @Nonnull List<CompileResult.UnresolvedExpression> expressions) {
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
                diagnoseGate(s, gate, ctx, expressions);
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

        Map<String, Set<Scope>> byIdentity = new LinkedHashMap<>();
        Map<String, String> identityTarget = new LinkedHashMap<>();
        for (Ordered o : ordered) {
            BasePolicy policy = o.source.kind().basePolicy();
            if (policy == BasePolicy.ENVIRONMENT) continue;
            Set<Scope> hits = byIdentity.computeIfAbsent(o.source.identity(), k -> new LinkedHashSet<>());
            identityTarget.putIfAbsent(o.source.identity(), o.source.targetRelative());
            for (Scope scope : scopes) {
                if (!scope.blocking && scope.covers(o.source.id())) hits.add(scope);
            }
        }

        List<Ordered> contributions = new ArrayList<>();
        for (Map.Entry<String, Set<Scope>> entry : byIdentity.entrySet()) {
            for (Scope scope : entry.getValue()) {
                contributions.add(new Ordered(
                        scope.contributionTo(identityTarget.get(entry.getKey()), entry.getKey()), scope.priority));
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
                    .thenComparingInt(o -> o.source.loadIndex())
                    .thenComparingInt(o -> rank(o.source.kind().basePolicy()))
                    .thenComparingInt(o -> o.source.id().getNameCount())
                    .thenComparing(o -> o.source.id().toString());

    private static int rank(@Nonnull BasePolicy policy) {
        return switch (policy) {
            case OPTIONAL -> 0;
            case SCOPE -> 1;
            case REQUIRED -> 2;
            case ENVIRONMENT -> 3;
        };
    }

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
        private PatchSource contributionTo(@Nonnull String target, @Nonnull String identity) {
            return new PatchSource(source.id(), source.loadIndex(), target, identity,
                    source.kind(), source.patchJson());
        }
    }
}
