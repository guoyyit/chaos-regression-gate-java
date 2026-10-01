package com.github.guoyyit.chaosregressiongatejava.gate.trace;

/**
 * Trace of a single HTTP attempt.
 */
public class AttemptTrace {
    private int attempt;
    private String code;
    private String resourceId;
    private Long version;
    private boolean replayed;

    public int getAttempt() { return attempt; }
    public void setAttempt(int attempt) { this.attempt = attempt; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public boolean isReplayed() { return replayed; }
    public void setReplayed(boolean replayed) { this.replayed = replayed; }
}
