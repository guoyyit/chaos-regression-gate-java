package com.github.guoyyit.chaosregressiongatejava.gate.trace;

/**
 * Trace of a dependency-boundary event observation.
 */
public class DependencyEventTrace {
    private String eventId;
    private String resourceId;
    private long version;
    private int order;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
}
