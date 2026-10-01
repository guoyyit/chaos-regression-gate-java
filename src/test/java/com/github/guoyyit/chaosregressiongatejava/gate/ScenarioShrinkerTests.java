package com.github.guoyyit.chaosregressiongatejava.gate;

import com.github.guoyyit.chaosregressiongatejava.gate.compare.ComparisonOutcome;
import com.github.guoyyit.chaosregressiongatejava.gate.compare.ComparisonResult;
import com.github.guoyyit.chaosregressiongatejava.gate.compare.TraceComparator;
import com.github.guoyyit.chaosregressiongatejava.gate.runner.RunResult;
import com.github.guoyyit.chaosregressiongatejava.gate.runner.ScenarioRunner;
import com.github.guoyyit.chaosregressiongatejava.gate.scenario.Scenario;
import com.github.guoyyit.chaosregressiongatejava.gate.scenario.ScenarioLoader;
import com.github.guoyyit.chaosregressiongatejava.gate.shrink.ScenarioShrinker;
import com.github.guoyyit.chaosregressiongatejava.gate.shrink.ShrinkResult;
import com.github.guoyyit.chaosregressiongatejava.gate.trace.Trace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioShrinkerTests {
    private final ScenarioLoader loader = new ScenarioLoader();
    private final ScenarioRunner runner = new ScenarioRunner(2000, 60000);

    @Test
    void shrinksToMinimalReproducer(@TempDir Path tempDir) {
        Scenario baselineScenario = loader.load(new File("src/test/resources/scenarios/basic-crud.json"));
        RunResult baselineRun = runner.run(baselineScenario, tempDir.resolve("baseline").toFile());
        assertTrue(baselineRun.isSuccess(), "baseline run failed: " + baselineRun.getFailureReason());
        Trace baseline = baselineRun.getTrace();

        Scenario failing = loader.load(new File("src/test/resources/scenarios/regression-stale-update.json"));
        RunResult failingRun = runner.run(failing, tempDir.resolve("failing").toFile());
        assertTrue(failingRun.isSuccess(), "failing run failed: " + failingRun.getFailureReason());
        ComparisonResult cmp = new TraceComparator().compare(baseline, failingRun.getTrace());
        assertEquals(ComparisonOutcome.REGRESSION, cmp.getOutcome());

        ShrinkResult shrinkResult = new ScenarioShrinker(runner).shrink(failing, baseline, tempDir.resolve("shrink").toFile());
        assertTrue(shrinkResult.isShrunk());
        assertTrue(shrinkResult.getFailureSignature().startsWith("REGRESSION:"));

        Scenario minimal = shrinkResult.getMinimalScenario();
        assertTrue(minimal.getRequests().size() < failing.getRequests().size(),
                "minimal should be smaller, got " + minimal.getRequests().size());

        RunResult minimalRun = runner.run(minimal, tempDir.resolve("minimal").toFile());
        assertTrue(minimalRun.isSuccess(), "minimal run failed: " + minimalRun.getFailureReason());
        ComparisonResult minimalCmp = new TraceComparator().compare(baseline, minimalRun.getTrace());
        assertEquals(ComparisonOutcome.REGRESSION, minimalCmp.getOutcome());

        Set<String> removed = Set.copyOf(shrinkResult.getRemovedRequests());
        System.out.println("removed requests: " + removed);
        System.out.println("minimal requests: " + minimal.getRequests().stream().map(r -> r.getId()).toList());
        assertTrue(removed.contains("read-1") || removed.contains("history-1"),
                "should remove unrelated requests, removed=" + removed);
    }

    @Test
    void noShrinkWhenNoRegression(@TempDir Path tempDir) {
        Scenario scenario = loader.load(new File("src/test/resources/scenarios/basic-crud.json"));
        RunResult run = runner.run(scenario, tempDir.toFile());
        assertTrue(run.isSuccess());
        Trace baseline = run.getTrace();
        ShrinkResult result = new ScenarioShrinker(runner).shrink(scenario, baseline, tempDir.resolve("shrink").toFile());
        assertFalse(result.isShrunk());
    }
}
