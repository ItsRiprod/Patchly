package com.riprod.patchly.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.riprod.patchly.core.JsonDeepMerge;
import com.riprod.patchly.core.MergeTable;
import com.riprod.patchly.core.compile.BaseResolver;
import com.riprod.patchly.core.compile.CompileResult;
import com.riprod.patchly.core.compile.PatchCompiler;
import com.riprod.patchly.core.compile.PatchSource;
import com.riprod.patchly.core.directive.PatchContext;
import com.riprod.patchly.source.SourceKind;
import com.riprod.patchly.source.kinds.PatchKind;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class CompilerBench {

    @Param({"50", "200", "800"})
    public int targets;

    @Param({"1", "4"})
    public int stackDepth;

    private static final SourceKind PATCH = new PatchKind();
    private static final PatchContext CTX = PatchContext.ALWAYS;

    private MergeTable table;
    private List<PatchSource> sources;
    private BaseResolver baseResolver;

    @Setup
    public void setup() {
        table = JsonDeepMerge.activeTable();

        Map<String, JsonObject> bases = new HashMap<>();
        sources = new ArrayList<>();
        int load = 0;
        for (int t = 0; t < targets; t++) {
            String target = "Server/Item/Items/Stress/Stress_Item_" + t + ".json";
            bases.put(target, baseItem(t));
            for (int d = 0; d < stackDepth; d++) {
                JsonObject p = new JsonObject();
                p.add("$Priority", new JsonPrimitive((d * 7 + t) % 10));
                p.add("ItemLevel", new JsonPrimitive(20 + d));
                JsonObject tags = new JsonObject();
                JsonArray fam = new JsonArray();
                fam.add("stress" + d);
                tags.add("Family+", fam);
                p.add("Tags", tags);
                sources.add(new PatchSource(
                        Path.of("p" + (load++) + ".patch"), load, target, PATCH, p));
            }
        }
        baseResolver = t -> bases.containsKey(t)
                ? new BaseResolver.ResolvedBase(t, bases.get(t)) : null;
    }

    private static JsonObject baseItem(int t) {
        JsonObject o = new JsonObject();
        o.add("Quality", new JsonPrimitive("Common"));
        o.add("ItemLevel", new JsonPrimitive(10));
        JsonObject tags = new JsonObject();
        JsonArray fam = new JsonArray();
        fam.add("base");
        tags.add("Family", fam);
        o.add("Tags", tags);
        return o;
    }

    @Benchmark
    public CompileResult compile() {
        return new PatchCompiler().compile(sources, baseResolver, CTX, table);
    }
}
