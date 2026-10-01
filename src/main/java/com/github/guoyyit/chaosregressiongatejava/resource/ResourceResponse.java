package com.github.guoyyit.chaosregressiongatejava.resource;

import java.util.Map;

/**
 * Response envelope for resource operations.
 */
public class ResourceResponse {
    private String code;
    private String id;
    private Long version;
    private Map<String, Object> data;
    private boolean replayed;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
    public boolean isReplayed() { return replayed; }
    public void setReplayed(boolean replayed) { this.replayed = replayed; }
}
