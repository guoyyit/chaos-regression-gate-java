package com.github.guoyyit.chaosregressiongatejava.gate;

import com.github.guoyyit.chaosregressiongatejava.gate.compare.ComparisonOutcome;
import com.github.guoyyit.chaosregressiongatejava.gate.compare.ComparisonResult;
import com.github.guoyyit.chaosregressiongatejava.gate.compare.TraceComparator;
import com.github.guoyyit.chaosregressiongatejava.gate.report.GateReport;
import com.github.guoyyit.chaosregressiongatejava.gate.report.ReportWriter;
import com.github.guoyyit.chaosregressiongatejava.gate.report.ScenarioReport;
import com.github.guoyyit.chaosregressiongatejava.gate.runner.RunResult;
import com.github.guoyyit.chaosregressiongatejava.gate.runner.ScenarioRunner;
import com.github.guoyyit.chaosregressiongatejava.gate.scenario.Scenario;
import com.github.guoyyit.chaosregressiongatejava.gate.scenario.ScenarioLoader;
import com.github.guoyyit.chaosregressiongatejava.gate.shrink.ScenarioShrinker;
import com.github.guoyyit.chaosregressiongatejava.gate.shrink.ShrinkResult;
import com.github.guoyyit.chaosregressiongatejava.gate.trace.Trace;
import com.github.guoyyit.chaosregressiongatejava.gate.trace.TraceWriter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * CLI entry point for the regression gate. Runs all scenarios concurrently,
 * compares against baselines, shrinks failures, and exits non-zero when a
 * regression or stable failure is detected.
 */
public class GateMain {
    private final GateConfig config;
    private final ScenarioLoader loader = new ScenarioLoader();
    private final TraceWriter traceWriter = new TraceWriter();
    private final ReportWriter reportWriter = new ReportWriter();

    public GateMain(GateConfig config) {
        this.config = config;
    }

    public static void main(String[] args) {
        GateConfig config = parseArgs(args);
        GateMain gate = new GateMain(config);
        int exitCode = gate.run();
        System.exit(exitCode);
    }

    public int run() {
        File scenariosDir = new File(config.getScenariosDir());
        File baselinesDir = new File(config.getBaselinesDir());
        File outputDir = new File(config.getOutputDir());
        outputDir.mkdirs();

        List<Scenario> scenarios = loader.loadDir(scenariosDir);
        if (scenarios.isEmpty()) {
            System.out.println("No scenarios found in " + scenariosDir);
            return 0;
        }

        ExecutorService pool = Executors.newFixedThreadPool(config.getParallelism());
        List<Future<ScenarioReport>> futures = new ArrayList<>();
        for (Scenario scenario : scenarios) {
            futures.add(pool.submit(() -> processScenario(scenario, baselinesDir, outputDir)));
        }

        List<ScenarioReport> reports = new ArrayList<>();
        for (Future<ScenarioReport> f : futures) {
            try {
                reports.add(f.get(config.getScenarioTimeoutMs() * 2, TimeUnit.MILLISECONDS));
            } catch (Exception e) {
                ScenarioReport sr = new ScenarioReport();
                sr.setName("unknown");
                sr.setVerdict("STABLE_FAILURE");
                RunResult rr = new RunResult();
                rr.setSuccess(false);
                rr.setFailureReason("scenario timed out or failed: " + e.getMessage());
                sr.setRunResult(rr);
                reports.add(sr);
            }
        }
        pool.shutdownNow();

        GateReport report = aggregate(reports);
        reportWriter.write(report, new File(outputDir, "gate-report.json"));
        System.out.println(reportWriter.toText(report));

        return "FAIL".equals(report.getVerdict()) ? 1 : 0;
    }

    private ScenarioReport processScenario(Scenario scenario, File baselinesDir, File outputDir) {
        ScenarioReport sr = new ScenarioReport();
        sr.setName(scenario.getName());
        File runDir = new File(outputDir, "runs/" + scenario.getName());
        runDir.mkdirs();

        ScenarioRunner runner = new ScenarioRunner(1000, config.getScenarioTimeoutMs());

        // Stability runs: run the same scenario+seed multiple times to detect flakiness.
        List<Trace> stabilityTraces = new ArrayList<>();
        RunResult lastRun = null;
        boolean stable = true;
        for (int i = 0; i < config.getStabilityRuns(); i++) {
            RunResult run = runner.run(scenario, new File(runDir, "stability-" + i));
            lastRun = run;
            if (!run.isSuccess()) {
                sr.setVerdict("STABLE_FAILURE");
                sr.setRunResult(run);
                return sr;
            }
            stabilityTraces.add(run.getTrace());
            if (i > 0 && !tracesEqual(stabilityTraces.get(0), run.getTrace())) {
                stable = false;
            }
        }
        sr.setRunResult(lastRun);

        if (!stable) {
            sr.setFlaky(true);
            sr.setVerdict("FLAKY");
            return sr;
        }

        Trace actual = stabilityTraces.get(0);
        traceWriter.write(actual, new File(runDir, "trace.json"));

        File baselineFile = new File(baselinesDir, scenario.getName() + ".json");
        if (!baselineFile.exists()) {
            if (config.isUpdateBaseline()) {
                traceWriter.write(actual, baselineFile);
                sr.setVerdict("EXPECTED_CHANGE");
                return sr;
            }
            sr.setVerdict("NO_BASELINE");
            return sr;
        }

        Trace baseline = traceWriter.read(baselineFile);
        ComparisonResult cmp = new TraceComparator().compare(baseline, actual);
        sr.setComparison(cmp);

        if (cmp.getOutcome() == ComparisonOutcome.REGRESSION) {
            sr.setVerdict("REGRESSION");
            // Auto-shrink to minimal reproducer
            try {
                ShrinkResult shrink = new ScenarioShrinker(runner).shrink(scenario, baseline, new File(outputDir, "shrink/" + scenario.getName()));
                if (shrink.isShrunk()) {
                    File minimalFile = new File(outputDir, "minimal/" + scenario.getName() + ".json");
                    writeScenario(shrink.getMinimalScenario(), minimalFile);
                    sr.setMinimalScenarioPath(minimalFile.getAbsolutePath());
                }
            } catch (Exception e) {
                // shrinking is best-effort
            }
            return sr;
        }

        if (cmp.getOutcome() == ComparisonOutcome.FLUCTUATION) {
            sr.setVerdict("FLUCTUATION");
            return sr;
        }

        if (config.isUpdateBaseline()) {
            traceWriter.write(actual, baselineFile);
            sr.setVerdict("EXPECTED_CHANGE");
            return sr;
        }

        sr.setVerdict("MATCH");
        return sr;
    }

    private boolean tracesEqual(Trace a, Trace b) {
        return new TraceComparator().compare(a, b).getOutcome() == ComparisonOutcome.MATCH;
    }

    private void writeScenario(Scenario scenario, File file) {
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT)
                    .writeValue(file, scenario);
        } catch (Exception e) {
            throw new IllegalStateException("failed to write scenario to " + file, e);
        }
    }

    private GateReport aggregate(List<ScenarioReport> reports) {
        GateReport report = new GateReport();
        report.setScenarios(reports);
        report.setTotal(reports.size());
        int passed = 0, regressions = 0, fluctuations = 0, expectedChanges = 0, stableFailures = 0, flakyFailures = 0;
        boolean fail = false;
        for (ScenarioReport sr : reports) {
            switch (sr.getVerdict()) {
                case "MATCH":
                    passed++;
                    break;
                case "REGRESSION":
                    regressions++;
                    fail = true;
                    break;
                case "FLUCTUATION":
                    fluctuations++;
                    passed++;
                    break;
                case "EXPECTED_CHANGE":
                    expectedChanges++;
                    passed++;
                    break;
                case "STABLE_FAILURE":
                    stableFailures++;
                    fail = true;
                    break;
                case "FLAKY":
                    flakyFailures++;
                    if ("FAIL".equalsIgnoreCase(config.getFlakyPolicy())) {
                        fail = true;
                    }
                    break;
                case "NO_BASELINE":
                    passed++;
                    break;
                default:
                    passed++;
            }
        }
        report.setPassed(passed);
        report.setRegressions(regressions);
        report.setFluctuations(fluctuations);
        report.setExpectedChanges(expectedChanges);
        report.setStableFailures(stableFailures);
        report.setFlakyFailures(flakyFailures);
        report.setVerdict(fail ? "FAIL" : "PASS");
        return report;
    }

    private static GateConfig parseArgs(String[] args) {
        GateConfig config = new GateConfig();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--scenarios":
                    config.setScenariosDir(args[++i]);
                    break;
                case "--baselines":
                    config.setBaselinesDir(args[++i]);
                    break;
                case "--output":
                    config.setOutputDir(args[++i]);
                    break;
                case "--stability-runs":
                    config.setStabilityRuns(Integer.parseInt(args[++i]));
                    break;
                case "--flaky-policy":
                    config.setFlakyPolicy(args[++i]);
                    break;
                case "--update-baseline":
                    config.setUpdateBaseline(true);
                    break;
                case "--parallelism":
                    config.setParallelism(Integer.parseInt(args[++i]));
                    break;
                case "--scenario-timeout":
                    config.setScenarioTimeoutMs(Long.parseLong(args[++i]));
                    break;
                default:
                    break;
            }
        }
        return config;
    }
}
