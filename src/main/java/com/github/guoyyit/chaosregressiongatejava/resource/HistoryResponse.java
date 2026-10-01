package com.github.guoyyit.chaosregressiongatejava.resource;

import java.util.List;

/**
 * Response envelope for resource version history.
 */
public class HistoryResponse {
    private String code;
    private String id;
    private List<ResourceRecord> versions;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public List<ResourceRecord> getVersions() { return versions; }
    public void setVersions(List<ResourceRecord> versions) { this.versions = versions; }
}
