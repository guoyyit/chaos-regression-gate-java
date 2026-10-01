package com.github.guoyyit.chaosregressiongatejava.resource;

import java.util.Map;

/**
 * Request body for creating a resource.
 */
public class CreateResourceRequest {
    private String idempotencyKey;
    private Map<String, Object> data;

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
}
