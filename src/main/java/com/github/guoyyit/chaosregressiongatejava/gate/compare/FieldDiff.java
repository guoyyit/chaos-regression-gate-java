package com.github.guoyyit.chaosregressiongatejava.gate.compare;

/**
 * A single field difference between two traces.
 */
public class FieldDiff {
    private String requestId;
    private String field;
    private String expected;
    private String actual;
    private boolean fluctuationOnly;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public String getExpected() { return expected; }
    public void setExpected(String expected) { this.expected = expected; }
    public String getActual() { return actual; }
    public void setActual(String actual) { this.actual = actual; }
    public boolean isFluctuationOnly() { return fluctuationOnly; }
    public void setFluctuationOnly(boolean fluctuationOnly) { this.fluctuationOnly = fluctuationOnly; }
}
