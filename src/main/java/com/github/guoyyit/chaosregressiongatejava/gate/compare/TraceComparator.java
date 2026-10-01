package com.github.guoyyit.chaosregressiongatejava.gate.compare;

import com.github.guoyyit.chaosregressiongatejava.gate.trace.AttemptTrace;
import com.github.guoyyit.chaosregressiongatejava.gate.trace.DependencyEventTrace;
import com.github.guoyyit.chaosregressiongatejava.gate.trace.RequestTrace;
import com.github.guoyyit.chaosregressiongatejava.gate.trace.Trace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Compares a run trace against a baseline trace and classifies each difference.
 * <p>
 * Behavior fields (result codes, versions, fault outcomes, dependency events)
 * that differ are classified as {@link ComparisonOutcome#REGRESSION}. Fields
 * that only reflect scheduling order (wave, dispatchIndex, completionIndex) are
 * classified as {@link ComparisonOutcome#FLUCTUATION}.
 */
public class TraceComparator {

    public ComparisonResult compare(Trace baseline, Trace actual) {
        ComparisonResult result = new ComparisonResult();
        List<FieldDiff> diffs = new ArrayList<>();
        boolean behaviorDiff = false;
        boolean fluctuationDiff = false;

        Set<String> baselineIds = new HashSet<>();
        for (RequestTrace r : baseline.getRequests()) {
            baselineIds.add(r.getId());
        }
        Set<String> actualIds = new HashSet<>();
        for (RequestTrace r : actual.getRequests()) {
            actualIds.add(r.getId());
        }

        for (String id : baselineIds) {
            if (!actualIds.contains(id)) {
                diffs.add(diff(id, "request", "present", "missing", false));
                behaviorDiff = true;
            }
        }

        for (RequestTrace actualReq : actual.getRequests()) {
            RequestTrace baselineReq = baseline.getRequests().stream()
                    .filter(r -> r.getId().equals(actualReq.getId()))
                    .findFirst().orElse(null);
            if (baselineReq == null) {
                diffs.add(diff(actualReq.getId(), "request", "missing", "present", false));
                behaviorDiff = true;
                continue;
            }
            // Behavior fields
            if (!eq(baselineReq.getMethod(), actualReq.getMethod())) {
                diffs.add(diff(actualReq.getId(), "method", baselineReq.getMethod(), actualReq.getMethod(), false));
                behaviorDiff = true;
            }
            if (!eq(baselineReq.getPath(), actualReq.getPath())) {
                diffs.add(diff(actualReq.getId(), "path", baselineReq.getPath(), actualReq.getPath(), false));
                behaviorDiff = true;
            }
            if (!eq(baselineReq.getFaults(), actualReq.getFaults())) {
                diffs.add(diff(actualReq.getId(), "faults", String.valueOf(baselineReq.getFaults()), String.valueOf(actualReq.getFaults()), false));
                behaviorDiff = true;
            }
            if (!eqAttempts(baselineReq.getAttempts(), actualReq.getAttempts())) {
                diffs.add(diff(actualReq.getId(), "attempts", String.valueOf(baselineReq.getAttempts()), String.valueOf(actualReq.getAttempts()), false));
                behaviorDiff = true;
            }
            if (!eqDependencyEvents(baselineReq.getDependencyEvents(), actualReq.getDependencyEvents())) {
                diffs.add(diff(actualReq.getId(), "dependencyEvents", String.valueOf(baselineReq.getDependencyEvents()), String.valueOf(actualReq.getDependencyEvents()), false));
                behaviorDiff = true;
            }
            // Fluctuation fields
            if (baselineReq.getWave() != actualReq.getWave()) {
                diffs.add(diff(actualReq.getId(), "wave", String.valueOf(baselineReq.getWave()), String.valueOf(actualReq.getWave()), true));
                fluctuationDiff = true;
            }
            if (baselineReq.getDispatchIndex() != actualReq.getDispatchIndex()) {
                diffs.add(diff(actualReq.getId(), "dispatchIndex", String.valueOf(baselineReq.getDispatchIndex()), String.valueOf(actualReq.getDispatchIndex()), true));
                fluctuationDiff = true;
            }
            if (baselineReq.getCompletionIndex() != actualReq.getCompletionIndex()) {
                diffs.add(diff(actualReq.getId(), "completionIndex", String.valueOf(baselineReq.getCompletionIndex()), String.valueOf(actualReq.getCompletionIndex()), true));
                fluctuationDiff = true;
            }
        }

        result.setDiffs(diffs);
        if (behaviorDiff) {
            result.setOutcome(ComparisonOutcome.REGRESSION);
        } else if (fluctuationDiff) {
            result.setOutcome(ComparisonOutcome.FLUCTUATION);
        } else {
            result.setOutcome(ComparisonOutcome.MATCH);
        }
        return result;
    }

    private boolean eqAttempts(List<AttemptTrace> a, List<AttemptTrace> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            AttemptTrace x = a.get(i);
            AttemptTrace y = b.get(i);
            if (x.getAttempt() != y.getAttempt()) return false;
            if (!eq(x.getCode(), y.getCode())) return false;
            if (!eq(x.getResourceId(), y.getResourceId())) return false;
            if (!eq(x.getVersion(), y.getVersion())) return false;
            if (x.isReplayed() != y.isReplayed()) return false;
        }
        return true;
    }

    private boolean eqDependencyEvents(List<DependencyEventTrace> a, List<DependencyEventTrace> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            DependencyEventTrace x = a.get(i);
            DependencyEventTrace y = b.get(i);
            if (!eq(x.getEventId(), y.getEventId())) return false;
            if (!eq(x.getResourceId(), y.getResourceId())) return false;
            if (x.getVersion() != y.getVersion()) return false;
            if (x.getOrder() != y.getOrder()) return false;
        }
        return true;
    }

    private FieldDiff diff(String requestId, String field, String expected, String actual, boolean fluctuationOnly) {
        FieldDiff d = new FieldDiff();
        d.setRequestId(requestId);
        d.setField(field);
        d.setExpected(expected);
        d.setActual(actual);
        d.setFluctuationOnly(fluctuationOnly);
        return d;
    }

    private boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}
