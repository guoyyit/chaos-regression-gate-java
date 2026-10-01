package com.github.guoyyit.chaosregressiongatejava.tests.gate;

import com.github.guoyyit.chaosregressiongatejava.gate.baseline.BaselineStore;
import com.github.guoyyit.chaosregressiongatejava.gate.baseline.DiffKind;
import com.github.guoyyit.chaosregressiongatejava.gate.baseline.TraceComparator;
import com.github.guoyyit.chaosregressiongatejava.gate.baseline.TraceDiff;
import com.github.guoyyit.chaosregressiongatejava.gate.model.Trace;
import com.github.guoyyit.chaosregressiongatejava.gate.model.TraceEntry;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaselineCompareTest {

    private final TraceComparator comparator = new TraceComparator();

    @TempDir
    Path baselinesDir;

    private TraceEntry entry(int seq, int status, String code, Map<String, Object> volatileFields) {
        return new TraceEntry(seq, "s" + seq, "RESPONSE", "GET /resources/x", status, code,
                "{\"code\":\"" + code + "\"}", List.of(), volatileFields);
    }

    @Test
    void identicalTracesHaveNoDiff() {
        Trace a = new Trace("sc", 1, List.of(entry(0, 200, "OK", Map.of("virtualClock", 1L))));
        Trace b = new Trace("sc", 1, List.of(entry(0, 200, "OK", Map.of("virtualClock", 1L))));
        assertEquals(DiffKind.NONE, comparator.classify(comparator.compare(a, b)));
    }

    @Test
    void volatileOnlyDiffIsNotARegression() {
        Trace baseline = new Trace("sc", 1, List.of(entry(0, 200, "OK", Map.of("virtualClock", 1L))));
        Trace actual = new Trace("sc", 1, List.of(entry(0, 200, "OK", Map.of("virtualClock", 9L))));
        List<TraceDiff> diffs = comparator.compare(baseline, actual);
        assertEquals(DiffKind.VOLATILE_ONLY, comparator.classify(diffs));
        assertTrue(diffs.stream().allMatch(TraceDiff::volatileField));
    }

    @Test
    void statusChangeIsBehavioral() {
        Trace baseline = new Trace("sc", 1, List.of(entry(0, 200, "OK", Map.of())));
        Trace actual = new Trace("sc", 1, List.of(entry(0, 409, "VERSION_CONFLICT", Map.of())));
        assertEquals(DiffKind.BEHAVIORAL, comparator.classify(comparator.compare(baseline, actual)));
    }

    @Test
    void extraEntriesAreBehavioral() {
        Trace baseline = new Trace("sc", 1, List.of(entry(0, 200, "OK", Map.of())));
        Trace actual = new Trace("sc", 1,
                List.of(entry(0, 200, "OK", Map.of()), entry(1, 200, "OK", Map.of())));
        assertEquals(DiffKind.BEHAVIORAL, comparator.classify(comparator.compare(baseline, actual)));
    }

    @Test
    void mixedVolatileAndBehavioralIsBehavioral() {
        Trace baseline = new Trace("sc", 1, List.of(entry(0, 200, "OK", Map.of("virtualClock", 1L))));
        Trace actual = new Trace("sc", 1, List.of(entry(0, 500, "OK", Map.of("virtualClock", 2L))));
        assertEquals(DiffKind.BEHAVIORAL, comparator.classify(comparator.compare(baseline, actual)));
    }

    @Test
    void baselineStoreRoundTrips() {
        BaselineStore store = new BaselineStore(baselinesDir);
        Trace trace = new Trace("sc", 3, List.of(entry(0, 200, "OK", Map.of("virtualClock", 1L))));
        store.save(trace);
        Optional<Trace> loaded = store.load("sc", 3);
        assertTrue(loaded.isPresent());
        assertEquals(DiffKind.NONE, comparator.classify(comparator.compare(trace, loaded.get())),
                "a stored baseline must compare clean against itself");
    }
}
