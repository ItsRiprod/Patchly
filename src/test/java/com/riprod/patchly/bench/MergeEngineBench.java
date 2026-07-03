package com.riprod.patchly.bench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.riprod.patchly.core.JsonDeepMerge;
import com.riprod.patchly.core.MergeTable;
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

import java.util.concurrent.TimeUnit;

// suspect E: $Match array merge. matchId scales O(patch x base); matchAll adds the
// onLocatorHit "merge into every match" pass -> cubic. plot avg-time vs size.
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class MergeEngineBench {

    @Param({"10", "100", "250", "500", "1000"})
    public int size;

    @Param({"matchId", "matchAll"})
    public String mode;

    private MergeTable table;
    private JsonObject base;
    private JsonObject patch;

    @Setup
    public void setup() {
        table = JsonDeepMerge.activeTable();

        JsonArray baseArr = new JsonArray();
        for (int i = 0; i < size; i++) {
            JsonObject e = new JsonObject();
            e.add("Id", new JsonPrimitive("m" + i));
            e.add("CalculationType", new JsonPrimitive("Additive"));
            e.add("Amount", new JsonPrimitive(1));
            baseArr.add(e);
        }
        base = new JsonObject();
        base.add("Modifiers", baseArr);

        JsonArray patchArr = new JsonArray();
        boolean matchAll = "matchAll".equals(mode);
        for (int i = 0; i < size; i++) {
            JsonObject e = new JsonObject();
            if (matchAll) {
                // matches EVERY base element -> onLocatorHit merges into all of them
                e.add("$Match", new JsonPrimitive("CalculationType"));
                e.add("CalculationType", new JsonPrimitive("Additive"));
                e.add("Boost", new JsonPrimitive(i));
            } else {
                e.add("$Match", new JsonPrimitive("Id"));
                e.add("Id", new JsonPrimitive("m" + i));
                e.add("Amount", new JsonPrimitive(99));
            }
            patchArr.add(e);
        }
        patch = new JsonObject();
        patch.add("Modifiers~", patchArr);
    }

    @Benchmark
    public JsonObject matchMerge() {
        return JsonDeepMerge.merge(base, patch, table);
    }
}
