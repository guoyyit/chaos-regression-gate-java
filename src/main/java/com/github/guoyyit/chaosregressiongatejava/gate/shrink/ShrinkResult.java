package com.github.guoyyit.chaosregressiongatejava.gate.shrink;

import com.github.guoyyit.chaosregressiongatejava.gate.scenario.Scenario;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of auto-shrinking a failing scenario.
 */
public class ShrinkResult {
    private boolean shrunk;
    private String failureSignature;
    private Scenario minimalScenario;
    private List<String> removedRequests = new ArrayList<>();
    private List<String> removedFaults = new ArrayList<>();

    public boolean isShrunk() { return shrunk; }
    public void setShrunk(boolean shrunk) { this.shrunk = shrunk; }
    public String getFailureSignature() { return failureSignature; }
    public void setFailureSignature(String failureSignature) { this.failureSignature = failureSignature; }
    public Scenario getMinimalScenario() { return minimalScenario; }
    public void setMinimalScenario(Scenario minimalScenario) { this.minimalScenario = minimalScenario; }
    public List<String> getRemovedRequests() { return removedRequests; }
    public void setRemovedRequests(List<String> removedRequests) { this.removedRequests = removedRequests; }
    public List<String> getRemovedFaults() { return removedFaults; }
    public void setRemovedFaults(List<String> removedFaults) { this.removedFaults = removedFaults; }
}
