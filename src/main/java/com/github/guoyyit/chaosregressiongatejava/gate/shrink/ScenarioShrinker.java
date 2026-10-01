package com.github.guoyyit.chaosregressiongatejava.gate.shrink;

import com.github.guoyyit.chaosregressiongatejava.gate.compare.ComparisonOutcome;
import com.github.guoyyit.chaosregressiongatejava.gate.compare.ComparisonResult;
import com.github.guoyyit.chaosregressiongatejava.gate.compare.TraceComparator;
import com.github.guoyyit.chaosregressiongatejava.gate.runner.RunResult;
import com.github.guoyyit.chaosregressiongatejava.gate.runner.ScenarioRunner;
import com.github.guoyyit.chaosregressiongatejava.gate.scenario.Scenario;
import com.github.guoyyit.chaosregressiongatejava.gate.scenario.ScenarioFault;
import com.github.guoyyit.chaosregressiongatejava.gate.scenario.ScenarioRequest;
import com.github.guoyyit.chaosregressiongatejava.gate.trace.Trace;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shrinks a failing scenario to a minimal scenario that still stably reproduces
 * the failure. Uses a greedy ddmin-style approach: repeatedly try removing each
 * request and each fault; keep the removal only if the failure signature is
 * preserved. The result is directly re-executable.
 */
public class ScenarioShrinker {
    private final ScenarioRunner runner;

    public ScenarioShrinker() {
        this(new ScenarioRunner());
    }

    public ScenarioShrinker(ScenarioRunner runner) {
        this.runner = runner;
    }

    public ShrinkResult shrink(Scenario scenario, Trace baseline, File runDir) {
        RunResult fullRun = runner.run(scenario, new File(runDir, "full"));
        String originalSignature = signature(scenario, fullRun, baseline);

        ShrinkResult result = new ShrinkResult();
        if (!originalSignature.startsWith("REGRESSION:") && !originalSignature.startsWith("FAILURE:")) {
            result.setShrunk(false);
            result.setFailureSignature(originalSignature);
            result.setMinimalScenario(scenario);
            return result;
        }

        Scenario current = copy(scenario);
        List<String> removedRequests = new ArrayList<>();
        List<String> removedFaults = new ArrayList<>();

        boolean changed = true;
        while (changed) {
            changed = false;
            for (String reqId : requestIds(current)) {
                if (current.getRequests().size() <= 1) {
                    break;
                }
                Scenario candidate = withoutRequest(current, reqId);
                RunResult candidateRun = runner.run(candidate, new File(runDir, "candidate-" + reqId));
                String candidateSignature = signature(candidate, candidateRun, baseline);
                if (candidateSignature.equals(originalSignature)) {
                    current = candidate;
                    removedRequests.add(reqId);
                    changed = true;
                    break;
                }
            }
            for (String faultId : faultIds(current)) {
                Scenario candidate = withoutFault(current, faultId);
                RunResult candidateRun = runner.run(candidate, new File(runDir, "candidate-fault-" + faultId));
                String candidateSignature = signature(candidate, candidateRun, baseline);
                if (candidateSignature.equals(originalSignature)) {
                    current = candidate;
                    removedFaults.add(faultId);
                    changed = true;
                    break;
                }
            }
        }

        result.setShrunk(!removedRequests.isEmpty() || !removedFaults.isEmpty());
        result.setFailureSignature(originalSignature);
        result.setMinimalScenario(current);
        result.setRemovedRequests(removedRequests);
        result.setRemovedFaults(removedFaults);
        return result;
    }

    /**
     * Computes a stable failure signature. For regressions, the signature is the
     * sorted set of behavior diffs for requests present in the current scenario
     * (missing-request diffs from shrinking are excluded). For stable failures,
     * the signature is the failure reason.
     */
    private String signature(Scenario scenario, RunResult run, Trace baseline) {
        if (!run.isSuccess()) {
            return "FAILURE:" + run.getFailureReason();
        }
        Set<String> currentIds = scenario.getRequests().stream()
                .map(ScenarioRequest::getId).collect(Collectors.toSet());
        ComparisonResult cmp = new TraceComparator().compare(baseline, run.getTrace());
        if (cmp.getOutcome() != ComparisonOutcome.REGRESSION) {
            return "NOT_REGRESSION:" + cmp.getOutcome();
        }
        List<String> sigs = cmp.getDiffs().stream()
                .filter(d -> !d.isFluctuationOnly())
                .filter(d -> currentIds.contains(d.getRequestId()))
                .map(d -> d.getRequestId() + ":" + d.getField())
                .sorted()
                .collect(Collectors.toList());
        return "REGRESSION:" + String.join(",", sigs);
    }

    private Scenario withoutRequest(Scenario s, String reqId) {
        Scenario c = copy(s);
        c.setRequests(c.getRequests().stream()
                .filter(r -> !r.getId().equals(reqId))
                .collect(Collectors.toList()));
        for (ScenarioRequest r : c.getRequests()) {
            r.setDependsOn(r.getDependsOn().stream()
                    .filter(d -> !d.equals(reqId))
                    .collect(Collectors.toList()));
        }
        c.setFaults(c.getFaults().stream()
                .filter(f -> !f.getRequest().equals(reqId))
                .collect(Collectors.toList()));
        return c;
    }

    private Scenario withoutFault(Scenario s, String faultId) {
        Scenario c = copy(s);
        c.setFaults(c.getFaults().stream()
                .filter(f -> !f.getId().equals(faultId))
                .collect(Collectors.toList()));
        return c;
    }

    private List<String> requestIds(Scenario s) {
        return s.getRequests().stream().map(ScenarioRequest::getId).collect(Collectors.toList());
    }

    private List<String> faultIds(Scenario s) {
        return s.getFaults().stream().map(ScenarioFault::getId).collect(Collectors.toList());
    }

    private Scenario copy(Scenario s) {
        Scenario c = new Scenario();
        c.setName(s.getName());
        c.setSeed(s.getSeed());
        c.setParallelism(s.getParallelism());
        c.setSerializeResourceAccess(s.getSerializeResourceAccess());
        List<ScenarioRequest> requests = new ArrayList<>();
        for (ScenarioRequest r : s.getRequests()) {
            ScenarioRequest cr = new ScenarioRequest();
            cr.setId(r.getId());
            cr.setMethod(r.getMethod());
            cr.setPath(r.getPath());
            cr.setBody(r.getBody());
            cr.setDependsOn(new ArrayList<>(r.getDependsOn()));
            requests.add(cr);
        }
        c.setRequests(requests);
        List<ScenarioFault> faults = new ArrayList<>();
        for (ScenarioFault f : s.getFaults()) {
            ScenarioFault cf = new ScenarioFault();
            cf.setId(f.getId());
            cf.setType(f.getType());
            cf.setBoundary(f.getBoundary());
            cf.setRequest(f.getRequest());
            cf.setDelayMs(f.getDelayMs());
            cf.setAfter(f.getAfter());
            faults.add(cf);
        }
        c.setFaults(faults);
        return c;
    }
}
