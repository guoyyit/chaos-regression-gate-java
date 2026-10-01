package com.github.guoyyit.chaosregressiongatejava.gate.compare;

/**
 * Outcome of comparing a run trace against a baseline.
 */
public enum ComparisonOutcome {
    MATCH,
    REGRESSION,
    FLUCTUATION,
    EXPECTED_CHANGE
}
