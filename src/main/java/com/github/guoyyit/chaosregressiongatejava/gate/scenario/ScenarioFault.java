package com.github.guoyyit.chaosregressiongatejava.gate.scenario;

/**
 * A fault declaration in a scenario.
 */
public class ScenarioFault {
    private String id;
    private String type;
    private String boundary;
    private String request;
    private long delayMs;
    private String after;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getBoundary() { return boundary; }
    public void setBoundary(String boundary) { this.boundary = boundary; }
    public String getRequest() { return request; }
    public void setRequest(String request) { this.request = request; }
    public long getDelayMs() { return delayMs; }
    public void setDelayMs(long delayMs) { this.delayMs = delayMs; }
    public String getAfter() { return after; }
    public void setAfter(String after) { this.after = after; }
}
