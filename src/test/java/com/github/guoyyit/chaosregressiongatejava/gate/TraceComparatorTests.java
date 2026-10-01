package com.github.guoyyit.chaosregressiongatejava.gate;

import com.github.guoyyit.chaosregressiongatejava.gate.compare.ComparisonOutcome;
import com.github.guoyyit.chaosregressiongatejava.gate.compare.ComparisonResult;
import com.github.guoyyit.chaosregressiongatejava.gate.compare.TraceComparator;
import com.github.guoyyit.chaosregressiongatejava.gate.trace.AttemptTrace;
import com.github.guoyyit.chaosregressiongatejava.gate.trace.RequestTrace;
import com.github.guoyyit.chaosregressiongatejava.gate.trace.Trace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TraceComparatorTests {
    private final TraceComparator comparator = new TraceComparator();

    private Trace baseTrace() {
        Trace t = new Trace();
        t.setScenario("s");
        t.setSeed(1);
        RequestTrace r1 = new RequestTrace();
        r1.setId("r1");
        r1.setMethod("POST");
        r1.setPath("/api/resources");
        r1.setWave(0);
        r1.setDispatchIndex(0);
        r1.setCompletionIndex(0);
        AttemptTrace a1 = new AttemptTrace();
        a1.setAttempt(1);
        a1.setCode("CREATED");
        a1.setResourceId("r-1");
        a1.setVersion(1L);
        r1.getAttempts().add(a1);
        t.getRequests().add(r1);

        RequestTrace r2 = new RequestTrace();
        r2.setId("r2");
        r2.setMethod("GET");
        r2.setPath("/api/resources/r-1");
        r2.setWave(1);
        r2.setDispatchIndex(1);
        r2.setCompletionIndex(1);
        AttemptTrace a2 = new AttemptTrace();
        a2.setAttempt(1);
        a2.setCode("OK");
        a2.setResourceId("r-1");
        a2.setVersion(1L);
        r2.getAttempts().add(a2);
        t.getRequests().add(r2);
        return t;
    }

    @Test
    void identicalTracesMatch() {
        ComparisonResult result = comparator.compare(baseTrace(), baseTrace());
        assertEquals(ComparisonOutcome.MATCH, result.getOutcome());
        assertTrue(result.getDiffs().isEmpty());
    }

    @Test
    void behaviorDiffIsRegression() {
        Trace actual = baseTrace();
        actual.getRequests().get(0).getAttempts().get(0).setCode("STALE_VERSION");
        ComparisonResult result = comparator.compare(baseTrace(), actual);
        assertEquals(ComparisonOutcome.REGRESSION, result.getOutcome());
        assertFalse(result.getDiffs().isEmpty());
    }

    @Test
    void versionDiffIsRegression() {
        Trace actual = baseTrace();
        actual.getRequests().get(1).getAttempts().get(0).setVersion(2L);
        ComparisonResult result = comparator.compare(baseTrace(), actual);
        assertEquals(ComparisonOutcome.REGRESSION, result.getOutcome());
    }

    @Test
    void fluctuationOnlyIsNotRegression() {
        Trace actual = baseTrace();
        actual.getRequests().get(0).setWave(5);
        actual.getRequests().get(0).setDispatchIndex(9);
        actual.getRequests().get(0).setCompletionIndex(9);
        ComparisonResult result = comparator.compare(baseTrace(), actual);
        assertEquals(ComparisonOutcome.FLUCTUATION, result.getOutcome());
    }

    @Test
    void missingRequestIsRegression() {
        Trace actual = baseTrace();
        actual.getRequests().remove(1);
        ComparisonResult result = comparator.compare(baseTrace(), actual);
        assertEquals(ComparisonOutcome.REGRESSION, result.getOutcome());
    }

    @Test
    void extraRequestIsRegression() {
        Trace actual = baseTrace();
        RequestTrace r3 = new RequestTrace();
        r3.setId("r3");
        r3.setMethod("GET");
        r3.setPath("/api/resources/r-1/history");
        actual.getRequests().add(r3);
        ComparisonResult result = comparator.compare(baseTrace(), actual);
        assertEquals(ComparisonOutcome.REGRESSION, result.getOutcome());
    }
}
