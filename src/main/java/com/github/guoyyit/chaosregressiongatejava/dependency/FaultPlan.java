package com.github.guoyyit.chaosregressiongatejava.dependency;

import java.util.ArrayList;
import java.util.List;

/**
 * Fault plan passed from the gate to the service for dependency-boundary injection.
 */
public class FaultPlan {
    private String scenario;
    private long seed;
    private List<DependencyFault> dependencyFaults = new ArrayList<>();

    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
    public long getSeed() { return seed; }
    public void setSeed(long seed) { this.seed = seed; }
    public List<DependencyFault> getDependencyFaults() { return dependencyFaults; }
    public void setDependencyFaults(List<DependencyFault> dependencyFaults) { this.dependencyFaults = dependencyFaults; }

    /**
     * A single dependency-boundary fault.
     */
    public static class DependencyFault {
        private String request;
        private String type;
        private long delayMs;
        private String afterRequest;

        public String getRequest() { return request; }
        public void setRequest(String request) { this.request = request; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public long getDelayMs() { return delayMs; }
        public void setDelayMs(long delayMs) { this.delayMs = delayMs; }
        public String getAfterRequest() { return afterRequest; }
        public void setAfterRequest(String afterRequest) { this.afterRequest = afterRequest; }
    }
}
