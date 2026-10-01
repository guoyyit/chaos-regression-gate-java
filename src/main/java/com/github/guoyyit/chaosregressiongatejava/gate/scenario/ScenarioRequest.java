package com.github.guoyyit.chaosregressiongatejava.gate.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A single request in a scenario.
 */
public class ScenarioRequest {
    private String id;
    private String method;
    private String path;
    private Map<String, Object> body;
    private List<String> dependsOn = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public Map<String, Object> getBody() { return body; }
    public void setBody(Map<String, Object> body) { this.body = body; }
    public List<String> getDependsOn() { return dependsOn; }
    public void setDependsOn(List<String> dependsOn) { this.dependsOn = dependsOn; }
}
