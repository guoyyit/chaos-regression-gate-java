package com.github.guoyyit.chaosregressiongatejava.gate.scenario;

import java.util.ArrayList;
import java.util.List;

/**
 * Declarative scenario: request sequence, concurrency and dependency behavior.
 */
public class Scenario {
    private String name;
    private long seed;
    private Integer parallelism;
    private Boolean serializeResourceAccess;
    private List<ScenarioRequest> requests = new ArrayList<>();
    private List<ScenarioFault> faults = new ArrayList<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getSeed() { return seed; }
    public void setSeed(long seed) { this.seed = seed; }
    public Integer getParallelism() { return parallelism; }
    public void setParallelism(Integer parallelism) { this.parallelism = parallelism; }
    public Boolean getSerializeResourceAccess() { return serializeResourceAccess; }
    public void setSerializeResourceAccess(Boolean serializeResourceAccess) { this.serializeResourceAccess = serializeResourceAccess; }
    public List<ScenarioRequest> getRequests() { return requests; }
    public void setRequests(List<ScenarioRequest> requests) { this.requests = requests; }
    public List<ScenarioFault> getFaults() { return faults; }
    public void setFaults(List<ScenarioFault> faults) { this.faults = faults; }
}
