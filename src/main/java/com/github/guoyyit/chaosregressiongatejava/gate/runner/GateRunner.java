package com.github.guoyyit.chaosregressiongatejava.gate.runner;

import com.github.guoyyit.chaosregressiongatejava.gate.GateConfig;
import com.github.guoyyit.chaosregressiongatejava.gate.ScenarioLoader;
import com.github.guoyyit.chaosregressiongatejava.gate.baseline.BaselineStore;
import com.github.guoyyit.chaosregressiongatejava.gate.baseline.DiffKind;
import com.github.guoyyit.chaosregressiongatejava.gate.baseline.TraceComparator;
import com.github.guoyyit.chaosregressiongatejava.gate.baseline.TraceDiff;
import com.github.guoyyit.chaosregressiongatejava.gate.harness.DependencyStubServer;
import com.github.guoyyit.chaosregressiongatejava.gate.harness.ServerHarness;
import com.github.guoyyit.chaosregressiongatejava.gate.model.Scenario;
import com.github.guoyyit.chaosregressiongatejava.gate.model.Trace;
import com.github.guoyyit.chaosregressiongatejava.gate.report.GateReport;
import com.github.guoyyit.chaosregressiongatejava.gate.report.ReportWriter;
import com.github.guoyyit.chaosregressiongatejava.gate.shrink.Shrinker;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Runs all scenarios in parallel with isolated ports and run directories. */
public class GateRunner {

    private final GateConfig config;
    private final ScenarioLoader loader = new ScenarioLoader();
    private final TraceComparator comparator = new TraceComparator();

    public GateRunner(GateConfig config) {
        this.config = config;
    }

    public GateReport runAll() {
        List<Scenario> scenarios = loader.loadAll(config.scenariosDir());
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, config.parallelism()));
        try {
            List<Future<List<GateReport.Entry>>> futures = new ArrayList<>();
            for (Scenario scenario : scenarios) {
                futures.add(pool.submit(() -> runScenarioGuarded(scenario)));
            }
            List<GateReport.Entry> entries = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                entries.addAll(await(futures.get(i), scenarios.get(i).name()));
            }
            entries.sort(Comparator.comparing(GateReport.Entry::scenario));
            GateReport report = new GateReport(entries);
            new ReportWriter().write(report, config.runsDir().resolve("gate-report.md"));
            return report;
        } finally {
            pool.shutdownNow();
        }
    }

    private List<GateReport.Entry> await(Future<List<GateReport.Entry>> future, String name) {
        try {
            return future.get(config.scenarioTimeoutSeconds() * 4L + 60L, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return List.of(new GateReport.Entry(name, GateReport.Status.ERROR,
                    "scenario exceeded overall time budget"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of(new GateReport.Entry(name, GateReport.Status.ERROR, "interrupted"));
        } catch (ExecutionException e) {
            return List.of(new GateReport.Entry(name, GateReport.Status.ERROR,
                    String.valueOf(e.getCause())));
        }
    }

    private List<GateReport.Entry> runScenarioGuarded(Scenario scenario) {
        try {
            return runScenario(scenario);
        } catch (Exception e) {
            return List.of(new GateReport.Entry(scenario.name(), GateReport.Status.ERROR,
                    String.valueOf(e)));
        }
    }

    /** Runs one scenario across all its seeds; every seed gets a fresh isolated harness
     *  so no mutable state leaks between seeds. */
    public List<GateReport.Entry> runScenario(Scenario scenario) {
        Path scenarioDir = config.runsDir().resolve(sanitize(scenario.name()));
        BaselineStore baselines = new BaselineStore(config.baselinesDir());
        List<GateReport.Entry> seedEntries = new ArrayList<>();
        for (long seed : scenario.seeds()) {
            try (DependencyStubServer dependency = DependencyStubServer.start();
                 ServerHarness server = ServerHarness.start(dependency.port())) {
                String baseUrl = "http://127.0.0.1:" + server.port();
                ScenarioRunner runner = new ScenarioRunner(scenarioDir);
                seedEntries.add(evaluateSeed(scenario, seed, runner, baselines, baseUrl, scenarioDir));
            }
        }
        return List.of(aggregate(scenario.name(), seedEntries));
    }

    private GateReport.Entry evaluateSeed(Scenario scenario, long seed, ScenarioRunner runner,
                                          BaselineStore baselines, String baseUrl, Path scenarioDir) {
        Trace trace;
        try {
            trace = runner.run(scenario, seed, baseUrl);
        } catch (Exception e) {
            return new GateReport.Entry(key(scenario.name(), seed), GateReport.Status.STABLE_FAILURE,
                    "execution failed: " + e.getMessage());
        }
        var baseline = baselines.load(scenario.name(), seed);
        if (baseline.isEmpty()) {
            if (config.baselineUpdateConfirmed()) {
                baselines.save(trace);
                return new GateReport.Entry(key(scenario.name(), seed), GateReport.Status.PASSED,
                        "baseline created");
            }
            return new GateReport.Entry(key(scenario.name(), seed), GateReport.Status.ERROR,
                    "no baseline; run with -Dgate.updateBaseline=true -Dgate.updateConfirm="
                            + GateConfig.CONFIRM_TOKEN);
        }
        List<TraceDiff> diffs = comparator.compare(baseline.get(), trace);
        DiffKind kind = comparator.classify(diffs);
        switch (kind) {
            case NONE -> {
                return new GateReport.Entry(key(scenario.name(), seed), GateReport.Status.PASSED, "");
            }
            case VOLATILE_ONLY -> {
                return new GateReport.Entry(key(scenario.name(), seed), GateReport.Status.VOLATILE_ONLY,
                        diffs.size() + " volatile-only diff(s)");
            }
            default -> {
                if (config.expectedChanges().contains(scenario.name())) {
                    return new GateReport.Entry(key(scenario.name(), seed),
                            GateReport.Status.EXPECTED_CHANGE,
                            diffs.size() + " behavioral diff(s), declared as expected change");
                }
                if (config.baselineUpdateConfirmed()) {
                    baselines.save(trace);
                    return new GateReport.Entry(key(scenario.name(), seed),
                            GateReport.Status.EXPECTED_CHANGE, "baseline updated after confirmation");
                }
                String shrunkPath = shrinkFailingScenario(scenario, seed, runner, baselines,
                        baseUrl, scenarioDir, diffs);
                return new GateReport.Entry(key(scenario.name(), seed), GateReport.Status.REGRESSION,
                        diffs.size() + " behavioral diff(s); first: " + diffs.get(0)
                                + "; minimal repro: " + shrunkPath);
            }
        }
    }

    private String shrinkFailingScenario(Scenario scenario, long seed, ScenarioRunner runner,
                                         BaselineStore baselines, String baseUrl, Path scenarioDir,
                                         List<TraceDiff> originalDiffs) {
        try {
            String signature = failureSignature(originalDiffs);
            Shrinker shrinker = new Shrinker();
            Scenario shrunk = shrinker.shrink(scenario, candidate -> {
                try {
                    Trace trace = runner.run(candidate, seed, baseUrl);
                    var baseline = baselines.load(scenario.name(), seed);
                    if (baseline.isEmpty()) {
                        return false;
                    }
                    List<TraceDiff> diffs = comparator.compare(baseline.get(), trace);
                    return failureSignature(diffs).equals(signature);
                } catch (Exception e) {
                    return false;
                }
            });
            Path persisted = shrinker.persist(shrunk, scenarioDir.resolve("shrunk"));
            return persisted.toString();
        } catch (Exception e) {
            return "<shrink failed: " + e.getMessage() + ">";
        }
    }

    private String failureSignature(List<TraceDiff> diffs) {
        return diffs.stream().filter(d -> !d.volatileField()).findFirst()
                .map(d -> d.field() + "=" + d.actualValue()).orElse("<none>");
    }

    private GateReport.Entry aggregate(String name, List<GateReport.Entry> seedEntries) {
        boolean allPassed = seedEntries.stream().allMatch(e -> e.status() == GateReport.Status.PASSED
                || e.status() == GateReport.Status.VOLATILE_ONLY
                || e.status() == GateReport.Status.EXPECTED_CHANGE);
        if (allPassed) {
            GateReport.Status status = seedEntries.stream().anyMatch(e -> e.status()
                    == GateReport.Status.EXPECTED_CHANGE) ? GateReport.Status.EXPECTED_CHANGE
                    : seedEntries.stream().anyMatch(e -> e.status() == GateReport.Status.VOLATILE_ONLY)
                            ? GateReport.Status.VOLATILE_ONLY : GateReport.Status.PASSED;
            return new GateReport.Entry(name, status, summarize(seedEntries));
        }
        boolean allFailedSame = seedEntries.stream()
                .allMatch(e -> e.status() == seedEntries.get(0).status());
        if (allFailedSame) {
            return new GateReport.Entry(name, seedEntries.get(0).status(), summarize(seedEntries));
        }
        return new GateReport.Entry(name, GateReport.Status.FLAKY,
                "mixed results across seeds: " + summarize(seedEntries));
    }

    private String summarize(List<GateReport.Entry> entries) {
        StringBuilder sb = new StringBuilder();
        for (GateReport.Entry e : entries) {
            if (e.detail() != null && !e.detail().isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                sb.append(e.scenario()).append(": ").append(e.detail());
            }
        }
        return sb.toString();
    }

    private String key(String scenario, long seed) {
        return scenario + "#seed-" + seed;
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
