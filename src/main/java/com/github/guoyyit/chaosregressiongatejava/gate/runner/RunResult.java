package com.github.guoyyit.chaosregressiongatejava.gate.runner;

import com.github.guoyyit.chaosregressiongatejava.gate.trace.Trace;

/**
 * Result of a single scenario run.
 */
public class RunResult {
    private boolean success;
    private String failureReason;
    private Trace trace;
    private long durationMs;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Trace getTrace() { return trace; }
    public void setTrace(Trace trace) { this.trace = trace; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
}
