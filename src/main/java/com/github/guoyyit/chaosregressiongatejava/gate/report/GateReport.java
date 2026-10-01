package com.github.guoyyit.chaosregressiongatejava.gate.report;

import java.util.List;

/** Aggregated gate result with a process exit code. */
public record GateReport(List<Entry> entries) {

    public enum Status {
        PASSED, REGRESSION, EXPECTED_CHANGE, VOLATILE_ONLY, STABLE_FAILURE, FLAKY, ERROR
    }

    public record Entry(String scenario, Status status, String detail) {
    }

    public boolean hasRegression() {
        return entries.stream().anyMatch(e -> e.status() == Status.REGRESSION
                || e.status() == Status.STABLE_FAILURE
                || e.status() == Status.ERROR);
    }

    public boolean hasFlaky() {
        return entries.stream().anyMatch(e -> e.status() == Status.FLAKY);
    }

    /** Exit code for build integration. flakyPolicy: FAIL | WARN | IGNORE. */
    public int exitCode(String flakyPolicy) {
        if (hasRegression()) {
            return 1;
        }
        if (hasFlaky() && "FAIL".equalsIgnoreCase(flakyPolicy)) {
            return 2;
        }
        return 0;
    }
}
