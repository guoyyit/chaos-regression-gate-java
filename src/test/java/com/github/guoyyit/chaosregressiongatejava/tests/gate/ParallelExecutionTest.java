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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Scenarios run concurrently with isolated ports and run directories;
 *  all of them deliberately use the same resource id so any shared mutable
 *  state between scenarios would show up as a regression. */
class ParallelExecutionTest {

    @TempDir
    Path workDir;

    private Scenario crudScenario(String name) {
        return new Scenario(name, List.of(3L), List.of(
                new Step("c1", "main", "POST", "/resources/shared-id", "{\"data\":\"v1\"}", List.of()),
                new Step("c2", "main", "PUT", "/resources/shared-id",
                        "{\"expectedVersion\":1,\"data\":\"v2\"}", List.of()),
                new Step("c3", "main", "GET", "/resources/shared-id", null, List.of())),
                List.of());
    }

    private GateConfig config(Path scenarios, Path baselines, boolean updateBaseline) {
        return new GateConfig(scenarios, baselines, workDir.resolve("runs"), 3, 60,
                "WARN", updateBaseline, updateBaseline ? GateConfig.CONFIRM_TOKEN : "", List.of());
    }

    @Test
    void scenariosRunInParallelWithoutInterference() throws Exception {
        Path scenariosDir = workDir.resolve("scenarios");
        Path baselinesDir = workDir.resolve("baselines");
        Files.createDirectories(scenariosDir);
        ScenarioLoader loader = new ScenarioLoader();
        List<String> names = List.of("par-a", "par-b", "par-c");
        for (String name : names) {
            loader.save(crudScenario(name), scenariosDir.resolve(name + ".json"));
        }

        GateReport seeding = new GateRunner(config(scenariosDir, baselinesDir, true)).runAll();
        assertEquals(3, seeding.entries().size());

        GateReport report = new GateRunner(config(scenariosDir, baselinesDir, false)).runAll();
        assertEquals(3, report.entries().size());
        for (GateReport.Entry entry : report.entries()) {
            assertEquals(GateReport.Status.PASSED, entry.status(),
                    "scenario " + entry.scenario() + " must be unaffected by neighbours: "
                            + entry.detail());
        }
        for (String name : names) {
            assertTrue(Files.exists(workDir.resolve("runs").resolve(name).resolve("traces")),
                    "each scenario gets its own run directory");
        }
        assertTrue(Files.exists(workDir.resolve("runs").resolve("gate-report.md")));
    }
}
