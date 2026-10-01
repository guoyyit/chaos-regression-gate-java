package com.github.guoyyit.chaosregressiongatejava.gate.trace;

import java.util.ArrayList;
import java.util.List;

/**
 * Normalized execution trace of a scenario run.
 * No ports, timestamps or random identifiers are stored.
 */
public class Trace {
    private String scenario;
    private long seed;
    private List<RequestTrace> requests = new ArrayList<>();

    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
    public long getSeed() { return seed; }
    public void setSeed(long seed) { this.seed = seed; }
    public List<RequestTrace> getRequests() { return requests; }
    public void setRequests(List<RequestTrace> requests) { this.requests = requests; }
}
