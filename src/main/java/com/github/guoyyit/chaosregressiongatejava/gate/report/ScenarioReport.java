package com.github.guoyyit.chaosregressiongatejava.gate.report;

import com.github.guoyyit.chaosregressiongatejava.gate.compare.ComparisonResult;
import com.github.guoyyit.chaosregressiongatejava.gate.runner.RunResult;

/**
 * Per-scenario report entry.
 */
public class ScenarioReport {
    private String name;
    private String verdict;
    private RunResult runResult;
    private ComparisonResult comparison;
    private boolean flaky;
    private String minimalScenarioPath;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }
    public RunResult getRunResult() { return runResult; }
    public void setRunResult(RunResult runResult) { this.runResult = runResult; }
    public ComparisonResult getComparison() { return comparison; }
    public void setComparison(ComparisonResult comparison) { this.comparison = comparison; }
    public boolean isFlaky() { return flaky; }
    public void setFlaky(boolean flaky) { this.flaky = flaky; }
    public String getMinimalScenarioPath() { return minimalScenarioPath; }
    public void setMinimalScenarioPath(String minimalScenarioPath) { this.minimalScenarioPath = minimalScenarioPath; }
}
