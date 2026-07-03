package com.riprod.patchly.bench;

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

// object-recursion + base.deepCopy() cost as nesting depth grows. baseline that
// the $Match curve is measured against (linear-in-depth, not quadratic).
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class DeepNestBench {

    @Param({"2", "6", "12", "24"})
    public int depth;

    private MergeTable table;
    private JsonObject base;
    private JsonObject patch;

    @Setup
    public void setup() {
        table = JsonDeepMerge.activeTable();
        base = nest(depth, "base");
        patch = nest(depth, "patch");
    }

    private static JsonObject nest(int depth, String tag) {
        JsonObject root = new JsonObject();
        JsonObject cur = root;
        for (int d = 0; d < depth; d++) {
            cur.add("Level", new JsonPrimitive(d));
            cur.add("Tag", new JsonPrimitive(tag));
            JsonObject child = new JsonObject();
            cur.add("Nested", child);
            cur = child;
        }
        cur.add("Leaf", new JsonPrimitive(true));
        return root;
    }

    @Benchmark
    public JsonObject deepMerge() {
        return JsonDeepMerge.merge(base, patch, table);
    }
}
