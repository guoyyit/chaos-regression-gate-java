package com.github.guoyyit.chaosregressiongatejava.resource;

import java.util.Map;

/**
 * A versioned resource record.
 */
public class ResourceRecord {
    private String id;
    private long version;
    private Map<String, Object> data;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
}
