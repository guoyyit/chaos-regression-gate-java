package com.github.guoyyit.chaosregressiongatejava.resource;

import java.util.Map;

/**
 * Request body for updating a resource with optimistic version check.
 */
public class UpdateResourceRequest {
    private long version;
    private Map<String, Object> data;

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
}
