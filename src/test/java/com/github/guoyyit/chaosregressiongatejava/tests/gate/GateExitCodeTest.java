package com.github.guoyyit.chaosregressiongatejava.tests.gate;

import com.github.guoyyit.chaosregressiongatejava.gate.GateConfig;
import com.github.guoyyit.chaosregressiongatejava.gate.ScenarioLoader;
import com.github.guoyyit.chaosregressiongatejava.gate.model.Scenario;
import com.github.guoyyit.chaosregressiongatejava.gate.model.Step;
import com.github.guoyyit.chaosregressiongatejava.gate.report.GateReport;
import com.github.guoyyit.chaosregressiongatejava.gate.runner.GateRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The build gate must exit non-zero on regression or stable failure, treat
 *  declared expected changes separately, and make flaky handling configurable. */
class GateExitCodeTest {

    @TempDir
    Path workDir;

    private Scenario scenario(String name, String finalData) {
        return new Scenario(name, List.of(9L), List.of(
                new Step("c1", "main", "POST", "/resources/r", "{\"data\":\"v1\"}", List.of()),
                new Step("c2", "main", "PUT", "/resources/r",
                        "{\"expectedVersion\":1,\"data\":\"" + finalData + "\"}", List.of()),
                new Step("c3", "main", "GET", "/resources/r", null, List.of())),
                List.of());
    }

    private GateConfig config(Path scenarios, Path baselines, List<String> expectedChanges) {
        return new GateConfig(scenarios, baselines, workDir.resolve("runs"), 2, 60,
                "WARN", false, "", expectedChanges);
    }

    private Path writeScenario(Scenario scenario) throws Exception {
        Path dir = workDir.resolve("scenarios-" + scenario.name());
        Files.createDirectories(dir);
        new ScenarioLoader().save(scenario, dir.resolve(scenario.name() + ".json"));
        return dir;
    }

    private Path seedBaseline(Scenario scenario) {
        Path baselines = workDir.resolve("baselines-" + scenario.name());
        GateConfig seeding = new GateConfig(workDir.resolve("scenarios-" + scenario.name()),
                baselines, workDir.resolve("runs-seed"), 1, 60, "WARN", true,
                GateConfig.CONFIRM_TOKEN, List.of());
        new GateRunner(seeding).runAll();
        return baselines;
    }

    @Test
    void behavioralChangeIsRegressionAndFailsBuild() throws Exception {
        Scenario original = scenario("regress", "v2");
        Path scenariosDir = writeScenario(original);
        Path baselines = seedBaseline(original);

        Scenario changed = scenario("regress", "v2-CHANGED");
        new ScenarioLoader().save(changed, scenariosDir.resolve("regress.json"));

        GateReport report = new GateRunner(config(scenariosDir, baselines, List.of())).runAll();
        assertEquals(GateReport.Status.REGRESSION, report.entries().get(0).status());
        assertEquals(1, report.exitCode("WARN"));
        assertTrue(report.entries().get(0).detail().contains("minimal repro"),
                "regression must point at a shrunk minimal repro");
        try (Stream<Path> shrunk = Files.list(workDir.resolve("runs").resolve("regress")
                .resolve("shrunk"))) {
            assertTrue(shrunk.findAny().isPresent(), "shrunk scenario must be persisted");
        }
    }

    @Test
    void declaredExpectedChangeDoesNotFailBuild() throws Exception {
        Scenario original = scenario("expected", "v2");
        Path scenariosDir = writeScenario(original);
        Path baselines = seedBaseline(original);

        Scenario changed = scenario("expected", "v2-CHANGED");
        new ScenarioLoader().save(changed, scenariosDir.resolve("expected.json"));

        GateReport report = new GateRunner(
                config(scenariosDir, baselines, List.of("expected"))).runAll();
        assertEquals(GateReport.Status.EXPECTED_CHANGE, report.entries().get(0).status());
        assertEquals(0, report.exitCode("WARN"));
    }

    @Test
    void flakyPolicyIsConfigurable() {
        GateReport flaky = new GateReport(List.of(
                new GateReport.Entry("sc", GateReport.Status.FLAKY, "seed 1 passed, seed 2 failed")));
        assertEquals(2, flaky.exitCode("FAIL"), "flakyPolicy=FAIL must fail the build");
        assertEquals(0, flaky.exitCode("WARN"));
        assertEquals(0, flaky.exitCode("IGNORE"));

        GateReport regression = new GateReport(List.of(
                new GateReport.Entry("sc", GateReport.Status.REGRESSION, "diff")));
        assertEquals(1, regression.exitCode("IGNORE"),
                "regressions fail the build regardless of flaky policy");
    }

    @Test
    void missingBaselineIsAnErrorUnlessConfirmed() throws Exception {
        Scenario scenario = scenario("nobase", "v2");
        Path scenariosDir = writeScenario(scenario);
        Path baselines = workDir.resolve("empty-baselines");
        GateReport report = new GateRunner(config(scenariosDir, baselines, List.of())).runAll();
        assertEquals(GateReport.Status.ERROR, report.entries().get(0).status());
        assertEquals(1, report.exitCode("WARN"));
    }
}
