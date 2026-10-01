package com.github.guoyyit.chaosregressiongatejava.gate;

/**
 * Gate configuration.
 */
public class GateConfig {
    private String scenariosDir;
    private String baselinesDir;
    private String outputDir;
    private int stabilityRuns = 1;
    private String flakyPolicy = "FAIL";
    private boolean updateBaseline = false;
    private int parallelism = 4;
    private long scenarioTimeoutMs = 60000;

    public String getScenariosDir() { return scenariosDir; }
    public void setScenariosDir(String scenariosDir) { this.scenariosDir = scenariosDir; }
    public String getBaselinesDir() { return baselinesDir; }
    public void setBaselinesDir(String baselinesDir) { this.baselinesDir = baselinesDir; }
    public String getOutputDir() { return outputDir; }
    public void setOutputDir(String outputDir) { this.outputDir = outputDir; }
    public int getStabilityRuns() { return stabilityRuns; }
    public void setStabilityRuns(int stabilityRuns) { this.stabilityRuns = stabilityRuns; }
    public String getFlakyPolicy() { return flakyPolicy; }
    public void setFlakyPolicy(String flakyPolicy) { this.flakyPolicy = flakyPolicy; }
    public boolean isUpdateBaseline() { return updateBaseline; }
    public void setUpdateBaseline(boolean updateBaseline) { this.updateBaseline = updateBaseline; }
    public int getParallelism() { return parallelism; }
    public void setParallelism(int parallelism) { this.parallelism = parallelism; }
    public long getScenarioTimeoutMs() { return scenarioTimeoutMs; }
    public void setScenarioTimeoutMs(long scenarioTimeoutMs) { this.scenarioTimeoutMs = scenarioTimeoutMs; }
}
