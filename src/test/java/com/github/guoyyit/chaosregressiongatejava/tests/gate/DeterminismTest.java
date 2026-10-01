package com.github.guoyyit.chaosregressiongatejava.tests.gate;

import com.github.guoyyit.chaosregressiongatejava.gate.ScenarioLoader;
import com.github.guoyyit.chaosregressiongatejava.gate.harness.DependencyStubServer;
import com.github.guoyyit.chaosregressiongatejava.gate.harness.ServerHarness;
import com.github.guoyyit.chaosregressiongatejava.gate.model.Scenario;
import com.github.guoyyit.chaosregressiongatejava.gate.model.Trace;
import com.github.guoyyit.chaosregressiongatejava.gate.runner.ScenarioRunner;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Same seed + same scenario must produce byte-identical traces. */
class DeterminismTest {

    @TempDir
    Path runDir;

    @Test
    void sameSeedProducesIdenticalTrace() {
        Scenario scenario = new ScenarioLoader().load(
                Path.of("src/test/resources/gate/scenarios/basic-crud.json"));
        Trace first = runOnce(scenario, 7L);
        Trace second = runOnce(scenario, 7L);
        assertEquals(first.entries(), second.entries(),
                "same seed must replay the identical trace");
        assertFalse(first.entries().isEmpty());
    }

    private Trace runOnce(Scenario scenario, long seed) {
        try (DependencyStubServer dependency = DependencyStubServer.start();
             ServerHarness server = ServerHarness.start(dependency.port())) {
            String baseUrl = "http://127.0.0.1:" + server.port();
            return new ScenarioRunner(runDir).run(scenario, seed, baseUrl);
        }
    }

    @Test
    void traceContainsNoVolatileContent() {
        Scenario scenario = new ScenarioLoader().load(
                Path.of("src/test/resources/gate/scenarios/basic-crud.json"));
        try (DependencyStubServer dependency = DependencyStubServer.start();
             ServerHarness server = ServerHarness.start(dependency.port())) {
            String baseUrl = "http://127.0.0.1:" + server.port();
            Trace trace = new ScenarioRunner(runDir).run(scenario, 7L, baseUrl);
            for (var entry : trace.entries()) {
                String content = String.valueOf(entry.body()) + entry.request();
                assertFalse(content.matches(".*localhost:\\d+.*"),
                        "trace must not contain ports: " + content);
                assertFalse(content.matches(".*\\d{4}-\\d{2}-\\d{2}T\\d{2}:.*"),
                        "trace must not contain timestamps: " + content);
            }
        }
    }
}
