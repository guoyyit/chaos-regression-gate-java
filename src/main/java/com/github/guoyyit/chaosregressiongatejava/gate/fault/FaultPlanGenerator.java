package com.github.guoyyit.chaosregressiongatejava.gate.fault;

import com.github.guoyyit.chaosregressiongatejava.dependency.FaultPlan;
import com.github.guoyyit.chaosregressiongatejava.gate.scenario.Scenario;
import com.github.guoyyit.chaosregressiongatejava.gate.scenario.ScenarioFault;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates the deterministic fault plan for a scenario. Faults are declared in
 * the scenario file; the generator validates them and splits them into
 * client-boundary faults (for the gate HTTP client) and a dependency-boundary
 * fault plan (passed to the service).
 */
public class FaultPlanGenerator {

    public List<InjectedFault> clientFaults(Scenario scenario) {
        Set<String> requestIds = new HashSet<>();
        for (var r : scenario.getRequests()) {
            requestIds.add(r.getId());
        }
        List<InjectedFault> faults = new ArrayList<>();
        for (ScenarioFault sf : scenario.getFaults()) {
            FaultType type = parseType(sf.getType());
            FaultBoundary boundary = parseBoundary(sf.getBoundary());
            validate(sf, type, requestIds);
            if (boundary == FaultBoundary.CLIENT) {
                faults.add(toInjected(sf, type));
            }
        }
        return faults;
    }

    public FaultPlan dependencyFaultPlan(Scenario scenario) {
        Set<String> requestIds = new HashSet<>();
        for (var r : scenario.getRequests()) {
            requestIds.add(r.getId());
        }
        FaultPlan plan = new FaultPlan();
        plan.setScenario(scenario.getName());
        plan.setSeed(scenario.getSeed());
        for (ScenarioFault sf : scenario.getFaults()) {
            FaultType type = parseType(sf.getType());
            FaultBoundary boundary = parseBoundary(sf.getBoundary());
            validate(sf, type, requestIds);
            if (boundary == FaultBoundary.DEPENDENCY) {
                FaultPlan.DependencyFault df = new FaultPlan.DependencyFault();
                df.setRequest(sf.getRequest());
                df.setType(type.name());
                df.setDelayMs(sf.getDelayMs());
                df.setAfterRequest(sf.getAfter());
                plan.getDependencyFaults().add(df);
            }
        }
        return plan;
    }

    private InjectedFault toInjected(ScenarioFault sf, FaultType type) {
        InjectedFault f = new InjectedFault();
        f.setId(sf.getId());
        f.setType(type);
        f.setBoundary(FaultBoundary.CLIENT);
        f.setRequestId(sf.getRequest());
        f.setDelayMs(sf.getDelayMs());
        f.setAfterRequestId(sf.getAfter());
        return f;
    }

    private void validate(ScenarioFault sf, FaultType type, Set<String> requestIds) {
        if (!requestIds.contains(sf.getRequest())) {
            throw new IllegalArgumentException("fault " + sf.getId() + " references unknown request " + sf.getRequest());
        }
        if (type == FaultType.OUT_OF_ORDER) {
            if (sf.getAfter() == null || sf.getAfter().isBlank()) {
                throw new IllegalArgumentException("OUT_OF_ORDER fault " + sf.getId() + " requires 'after'");
            }
            if (!requestIds.contains(sf.getAfter())) {
                throw new IllegalArgumentException("OUT_OF_ORDER fault " + sf.getId() + " references unknown request " + sf.getAfter());
            }
            if (sf.getAfter().equals(sf.getRequest())) {
                throw new IllegalArgumentException("OUT_OF_ORDER fault " + sf.getId() + " cannot reference itself");
            }
        }
        if (type == FaultType.LATENCY && sf.getDelayMs() < 0) {
            throw new IllegalArgumentException("LATENCY fault " + sf.getId() + " has negative delayMs");
        }
    }

    private FaultType parseType(String raw) {
        try {
            return FaultType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown fault type: " + raw);
        }
    }

    private FaultBoundary parseBoundary(String raw) {
        try {
            return FaultBoundary.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown fault boundary: " + raw);
        }
    }
}
