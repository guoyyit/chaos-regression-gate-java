package com.github.guoyyit.chaosregressiongatejava.gate;

import com.github.guoyyit.chaosregressiongatejava.gate.runner.RunResult;
import com.github.guoyyit.chaosregressiongatejava.gate.runner.ScenarioRunner;
import com.github.guoyyit.chaosregressiongatejava.gate.scenario.Scenario;
import com.github.guoyyit.chaosregressiongatejava.gate.scenario.ScenarioLoader;
import com.github.guoyyit.chaosregressiongatejava.gate.trace.RequestTrace;
import com.github.guoyyit.chaosregressiongatejava.gate.trace.Trace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioRunnerTests {
    private final ScenarioLoader loader = new ScenarioLoader();

    @Test
    void basicCrudTrace(@TempDir Path tempDir) {
        Scenario scenario = loader.load(new File("src/test/resources/scenarios/basic-crud.json"));
        RunResult result = new ScenarioRunner().run(scenario, tempDir.toFile());
        assertTrue(result.isSuccess(), "run should succeed: " + result.getFailureReason());
        Trace trace = result.getTrace();
        assertEquals("basic-crud", trace.getScenario());
        assertEquals(5, trace.getRequests().size());

        RequestTrace create = trace.getRequests().stream().filter(r -> "create-1".equals(r.getId())).findFirst().orElseThrow();
        assertEquals("CREATED", create.getAttempts().get(0).getCode());
        assertEquals(1L, create.getAttempts().get(0).getVersion());
        assertEquals("r-1", create.getAttempts().get(0).getResourceId());

        RequestTrace read1 = trace.getRequests().stream().filter(r -> "read-1".equals(r.getId())).findFirst().orElseThrow();
        assertEquals("OK", read1.getAttempts().get(0).getCode());
        assertEquals(1L, read1.getAttempts().get(0).getVersion());

        RequestTrace update1 = trace.getRequests().stream().filter(r -> "update-1".equals(r.getId())).findFirst().orElseThrow();
        assertEquals("OK", update1.getAttempts().get(0).getCode());
        assertEquals(2L, update1.getAttempts().get(0).getVersion());

        RequestTrace read2 = trace.getRequests().stream().filter(r -> "read-2".equals(r.getId())).findFirst().orElseThrow();
        assertEquals(2L, read2.getAttempts().get(0).getVersion());

        RequestTrace history = trace.getRequests().stream().filter(r -> "history-1".equals(r.getId())).findFirst().orElseThrow();
        assertEquals("OK", history.getAttempts().get(0).getCode());
    }

    @Test
    void staleWriteRejected(@TempDir Path tempDir) {
        Scenario scenario = loader.load(new File("src/test/resources/scenarios/stale-write.json"));
        RunResult result = new ScenarioRunner().run(scenario, tempDir.toFile());
        assertTrue(result.isSuccess());
        Trace trace = result.getTrace();
        RequestTrace update = trace.getRequests().stream().filter(r -> "update-stale".equals(r.getId())).findFirst().orElseThrow();
        assertEquals("STALE_VERSION", update.getAttempts().get(0).getCode());
        assertEquals(1L, update.getAttempts().get(0).getVersion());
        RequestTrace read = trace.getRequests().stream().filter(r -> "read-1".equals(r.getId())).findFirst().orElseThrow();
        assertEquals(1L, read.getAttempts().get(0).getVersion());
    }
}
