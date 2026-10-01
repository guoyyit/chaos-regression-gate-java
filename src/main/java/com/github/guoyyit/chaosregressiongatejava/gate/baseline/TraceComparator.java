package com.github.guoyyit.chaosregressiongatejava.gate.baseline;

import com.github.guoyyit.chaosregressiongatejava.gate.model.Trace;
import com.github.guoyyit.chaosregressiongatejava.gate.model.TraceEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Compares traces entry by entry, classifying differences. */
public class TraceComparator {

    public List<TraceDiff> compare(Trace baseline, Trace actual) {
        List<TraceDiff> diffs = new ArrayList<>();
        List<TraceEntry> expected = baseline.entries();
        List<TraceEntry> observed = actual.entries();
        int common = Math.min(expected.size(), observed.size());
        for (int i = 0; i < common; i++) {
            compareEntry(i, expected.get(i), observed.get(i), diffs);
        }
        for (int i = common; i < expected.size(); i++) {
            diffs.add(new TraceDiff(i, "entry", describe(expected.get(i)), "<missing>", false));
        }
        for (int i = common; i < observed.size(); i++) {
            diffs.add(new TraceDiff(i, "entry", "<missing>", describe(observed.get(i)), false));
        }
        return diffs;
    }

    private void compareEntry(int index, TraceEntry expected, TraceEntry actual, List<TraceDiff> diffs) {
        addDiff(index, "stepId", expected.stepId(), actual.stepId(), false, diffs);
        addDiff(index, "action", expected.action(), actual.action(), false, diffs);
        addDiff(index, "request", expected.request(), actual.request(), false, diffs);
        addDiff(index, "status", expected.status(), actual.status(), false, diffs);
        addDiff(index, "code", expected.code(), actual.code(), false, diffs);
        addDiff(index, "body", expected.body(), actual.body(), false, diffs);
        addDiff(index, "faultsApplied", expected.faultsApplied(), actual.faultsApplied(), false, diffs);
        addDiff(index, "volatileFields", expected.volatileFields(), actual.volatileFields(), true, diffs);
    }

    private void addDiff(int index, String field, Object expected, Object actual,
                         boolean volatileField, List<TraceDiff> diffs) {
        boolean equal = volatileField
                ? Objects.equals(normalizeVolatile(expected), normalizeVolatile(actual))
                : Objects.equals(expected, actual);
        if (!equal) {
            diffs.add(new TraceDiff(index, field, String.valueOf(expected), String.valueOf(actual),
                    volatileField));
        }
    }

    /** Volatile fields compare by string form so number types (Long vs Integer)
     *  surviving JSON round-trips do not produce phantom diffs. */
    private Object normalizeVolatile(Object value) {
        if (value instanceof Map<?, ?> map) {
            var normalized = new java.util.TreeMap<String, String>();
            map.forEach((k, v) -> normalized.put(String.valueOf(k), String.valueOf(v)));
            return normalized;
        }
        return value == null ? null : String.valueOf(value);
    }

    private String describe(TraceEntry entry) {
        return entry.action() + " " + entry.request() + " -> " + entry.status();
    }

    public DiffKind classify(List<TraceDiff> diffs) {
        if (diffs.isEmpty()) {
            return DiffKind.NONE;
        }
        boolean onlyVolatile = diffs.stream().allMatch(TraceDiff::volatileField);
        return onlyVolatile ? DiffKind.VOLATILE_ONLY : DiffKind.BEHAVIORAL;
    }
}
