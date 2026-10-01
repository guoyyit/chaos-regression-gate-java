package com.github.guoyyit.chaosregressiongatejava.gate;

import com.github.guoyyit.chaosregressiongatejava.gate.runner.RunResult;
import com.github.guoyyit.chaosregressiongatejava.gate.runner.ScenarioRunner;
import com.github.guoyyit.chaosregressiongatejava.gate.scenario.Scenario;
import com.github.guoyyit.chaosregressiongatejava.gate.scenario.ScenarioLoader;
import com.github.guoyyit.chaosregressiongatejava.gate.trace.AttemptTrace;
import com.github.guoyyit.chaosregressiongatejava.gate.trace.DependencyEventTrace;
import com.github.guoyyit.chaosregressiongatejava.gate.trace.RequestTrace;
import com.github.guoyyit.chaosregressiongatejava.gate.trace.Trace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FaultInjectionTests {
    private final ScenarioLoader loader = new ScenarioLoader();
    private final ScenarioRunner runner = new ScenarioRunner(2000, 60000);

    private Trace run(String scenarioFile, Path tempDir) {
        Scenario scenario = loader.load(new File("src/test/resources/scenarios/" + scenarioFile));
        RunResult result = runner.run(scenario, tempDir.toFile());
        assertTrue(result.isSuccess(), "run should succeed: " + result.getFailureReason());
        return result.getTrace();
    }

    private RequestTrace req(Trace t, String id) {
        return t.getRequests().stream().filter(r -> id.equals(r.getId())).findFirst().orElseThrow();
    }

    @Test
    void latencyStillSucceeds(@TempDir Path tempDir) {
        Trace t = run("fault-latency.json", tempDir);
        assertEquals("CREATED", req(t, "create-1").getAttempts().get(0).getCode());
        assertEquals("LATENCY:CLIENT", req(t, "create-1").getFaults().get(0));
    }

    @Test
    void timeoutProducesTimeoutCode(@TempDir Path tempDir) {
        Trace t = run("fault-timeout.json", tempDir);
        AttemptTrace a = req(t, "create-1").getAttempts().get(0);
        assertEquals("TIMEOUT", a.getCode());
        assertNull(a.getResourceId());
    }

    @Test
    void connectionResetProducesFailureCode(@TempDir Path tempDir) {
        Trace t = run("fault-connreset.json", tempDir);
        AttemptTrace a = req(t, "create-1").getAttempts().get(0);
        assertEquals("CONNECTION_FAILURE", a.getCode());
    }

    @Test
    void duplicateDeliveryReplaysIdempotently(@TempDir Path tempDir) {
        Trace t = run("fault-duplicate.json", tempDir);
        List<AttemptTrace> attempts = req(t, "create-1").getAttempts();
        assertEquals(2, attempts.size());
        assertEquals("CREATED", attempts.get(0).getCode());
        assertEquals(1L, attempts.get(0).getVersion());
        assertEquals("OK", attempts.get(1).getCode());
        assertTrue(attempts.get(1).isReplayed());
        assertEquals(1L, attempts.get(1).getVersion());
    }

    @Test
    void outOfOrderHoldsRequest(@TempDir Path tempDir) {
        Trace t = run("fault-outoforder.json", tempDir);
        // read-before runs first (sees v1), then update-1 (v2)
        assertEquals(1L, req(t, "read-before").getAttempts().get(0).getVersion());
        assertEquals(2L, req(t, "update-1").getAttempts().get(0).getVersion());
        // update-1 is in a later wave than read-before
        assertTrue(req(t, "update-1").getWave() > req(t, "read-before").getWave());
    }

    @Test
    void failMidwayCommitsDataButReturnsFailure(@TempDir Path tempDir) {
        Trace t = run("fault-failmidway.json", tempDir);
        // update-1 returns DEPENDENCY_FAILURE but the version is committed
        AttemptTrace update = req(t, "update-1").getAttempts().get(0);
        assertEquals("DEPENDENCY_FAILURE", update.getCode());
        assertEquals(2L, update.getVersion());
        // subsequent read shows the committed version
        assertEquals(2L, req(t, "read-1").getAttempts().get(0).getVersion());
    }

    @Test
    void dependencyLatencySucceeds(@TempDir Path tempDir) {
        Trace t = run("fault-dep-latency.json", tempDir);
        assertEquals("OK", req(t, "update-1").getAttempts().get(0).getCode());
    }

    @Test
    void dependencyDuplicateRecordsTwoObservations(@TempDir Path tempDir) {
        Trace t = run("fault-dep-duplicate.json", tempDir);
        RequestTrace update = req(t, "update-1");
        assertEquals("OK", update.getAttempts().get(0).getCode());
        List<DependencyEventTrace> events = update.getDependencyEvents();
        assertEquals(2, events.size());
        assertEquals(events.get(0).getEventId(), events.get(1).getEventId());
    }

    @Test
    void dependencyOutOfOrderSwapsDelivery(@TempDir Path tempDir) {
        Trace t = run("fault-dep-outoforder.json", tempDir);
        // create-1's event is held until create-2's event is delivered
        List<DependencyEventTrace> events1 = req(t, "create-1").getDependencyEvents();
        List<DependencyEventTrace> events2 = req(t, "create-2").getDependencyEvents();
        assertEquals(1, events1.size());
        assertEquals(1, events2.size());
        // create-2's event delivered before create-1's event (out of order)
        assertTrue(events2.get(0).getOrder() < events1.get(0).getOrder());
    }
}
