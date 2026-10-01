package com.github.guoyyit.chaosregressiongatejava.gate.compare;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of a baseline comparison.
 */
public class ComparisonResult {
    private ComparisonOutcome outcome;
    private List<FieldDiff> diffs = new ArrayList<>();

    public ComparisonOutcome getOutcome() { return outcome; }
    public void setOutcome(ComparisonOutcome outcome) { this.outcome = outcome; }
    public List<FieldDiff> getDiffs() { return diffs; }
    public void setDiffs(List<FieldDiff> diffs) { this.diffs = diffs; }
}
