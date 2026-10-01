package com.github.guoyyit.chaosregressiongatejava.gate.fault;

/**
 * A resolved fault instance attached to a request.
 */
public class InjectedFault {
    private String id;
    private FaultType type;
    private FaultBoundary boundary;
    private String requestId;
    private long delayMs;
    private String afterRequestId;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public FaultType getType() { return type; }
    public void setType(FaultType type) { this.type = type; }
    public FaultBoundary getBoundary() { return boundary; }
    public void setBoundary(FaultBoundary boundary) { this.boundary = boundary; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public long getDelayMs() { return delayMs; }
    public void setDelayMs(long delayMs) { this.delayMs = delayMs; }
    public String getAfterRequestId() { return afterRequestId; }
    public void setAfterRequestId(String afterRequestId) { this.afterRequestId = afterRequestId; }
}
