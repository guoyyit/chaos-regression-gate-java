package com.github.guoyyit.chaosregressiongatejava.gate.report;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated gate report.
 */
public class GateReport {
    private String verdict;
    private int total;
    private int passed;
    private int regressions;
    private int fluctuations;
    private int expectedChanges;
    private int stableFailures;
    private int flakyFailures;
    private List<ScenarioReport> scenarios = new ArrayList<>();

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public int getPassed() { return passed; }
    public void setPassed(int passed) { this.passed = passed; }
    public int getRegressions() { return regressions; }
    public void setRegressions(int regressions) { this.regressions = regressions; }
    public int getFluctuations() { return fluctuations; }
    public void setFluctuations(int fluctuations) { this.fluctuations = fluctuations; }
    public int getExpectedChanges() { return expectedChanges; }
    public void setExpectedChanges(int expectedChanges) { this.expectedChanges = expectedChanges; }
    public int getStableFailures() { return stableFailures; }
    public void setStableFailures(int stableFailures) { this.stableFailures = stableFailures; }
    public int getFlakyFailures() { return flakyFailures; }
    public void setFlakyFailures(int flakyFailures) { this.flakyFailures = flakyFailures; }
    public List<ScenarioReport> getScenarios() { return scenarios; }
    public void setScenarios(List<ScenarioReport> scenarios) { this.scenarios = scenarios; }
}
