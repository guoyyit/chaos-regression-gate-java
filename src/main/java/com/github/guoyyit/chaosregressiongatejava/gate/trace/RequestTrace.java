package com.github.guoyyit.chaosregressiongatejava.gate.trace;

import java.util.ArrayList;
import java.util.List;

/**
 * Trace of a single request: its attempts, faults and dependency observations.
 */
public class RequestTrace {
    private String id;
    private String method;
    private String path;
    private int wave;
    private int dispatchIndex;
    private int completionIndex;
    private List<AttemptTrace> attempts = new ArrayList<>();
    private List<String> faults = new ArrayList<>();
    private List<DependencyEventTrace> dependencyEvents = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public int getWave() { return wave; }
    public void setWave(int wave) { this.wave = wave; }
    public int getDispatchIndex() { return dispatchIndex; }
    public void setDispatchIndex(int dispatchIndex) { this.dispatchIndex = dispatchIndex; }
    public int getCompletionIndex() { return completionIndex; }
    public void setCompletionIndex(int completionIndex) { this.completionIndex = completionIndex; }
    public List<AttemptTrace> getAttempts() { return attempts; }
    public void setAttempts(List<AttemptTrace> attempts) { this.attempts = attempts; }
    public List<String> getFaults() { return faults; }
    public void setFaults(List<String> faults) { this.faults = faults; }
    public List<DependencyEventTrace> getDependencyEvents() { return dependencyEvents; }
    public void setDependencyEvents(List<DependencyEventTrace> dependencyEvents) { this.dependencyEvents = dependencyEvents; }
}
