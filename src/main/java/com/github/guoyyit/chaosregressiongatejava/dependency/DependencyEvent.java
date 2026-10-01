package com.github.guoyyit.chaosregressiongatejava.dependency;

/**
 * An event published to the downstream dependency (event notifier).
 */
public class DependencyEvent {
    private String eventId;
    private String resourceId;
    private long version;
    private String requestId;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
