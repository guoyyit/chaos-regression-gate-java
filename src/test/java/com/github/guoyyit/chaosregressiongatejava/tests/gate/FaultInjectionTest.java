package com.github.guoyyit.chaosregressiongatejava.tests.gate;

import com.github.guoyyit.chaosregressiongatejava.gate.ScenarioLoader;
import com.github.guoyyit.chaosregressiongatejava.gate.harness.DependencyStubServer;
import com.github.guoyyit.chaosregressiongatejava.gate.harness.ServerHarness;
import com.github.guoyyit.chaosregressiongatejava.gate.model.Scenario;
import com.github.guoyyit.chaosregressiongatejava.gate.model.Trace;
import com.github.guoyyit.chaosregressiongatejava.gate.model.TraceEntry;
import com.github.guoyyit.chaosregressiongatejava.gate.runner.ScenarioRunner;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies each injectable fault produces its stable, observable signature. */
class FaultInjectionTest {

    @TempDir
    Path runDir;

    private Trace runFaultScenario() {
        Scenario scenario = new ScenarioLoader().load(
                Path.of("src/test/resources/gate/scenarios/fault-injection.json"));
        try (DependencyStubServer dependency = DependencyStubServer.start();
             ServerHarness server = ServerHarness.start(dependency.port())) {
            return new ScenarioRunner(runDir).run(scenario, 11L, "http://127.0.0.1:" + server.port());
        }
    }

    @Test
    void duplicateDeliversRequestTwice() {
        Trace trace = runFaultScenario();
        List<TraceEntry> c1 = entriesOf(trace, "c1");
        assertEquals(2, c1.size(), "duplicate fault must produce two attempts");
        assertEquals(201, c1.get(0).status());
        assertEquals("ALREADY_EXISTS", c1.get(1).code());
    }

    @Test
    void timeoutAndConnectionResetAreSynthesized() {
        Trace trace = runFaultScenario();
        TraceEntry timeout = entriesOf(trace, "c3").get(0);
        assertEquals(598, timeout.status());
        assertEquals("CLIENT_TIMEOUT", timeout.code());
        TraceEntry reset = entriesOf(trace, "c4").get(0);
        assertEquals(597, reset.status());
        assertEquals("CONNECTION_RESET", reset.code());
    }

    @Test
    void midwayFailureLeavesPartialState() {
        Trace trace = runFaultScenario();
        TraceEntry create = entriesOf(trace, "c5").get(0);
        assertEquals(500, create.status());
        assertEquals("DEPENDENCY_FAILURE", create.code());
        TraceEntry read = entriesOf(trace, "c6").get(0);
        assertEquals(200, read.status(), "mutation committed before the mid-processing failure");
    }

    @Test
    void faultsAreRecordedInTrace() {
        Trace trace = runFaultScenario();
        assertTrue(entriesOf(trace, "c1").get(0).faultsApplied().contains("DUPLICATE"));
        assertTrue(trace.entries().get(0).action().equals("DEP_FAULTS_INSTALLED"));
    }

    private List<TraceEntry> entriesOf(Trace trace, String stepId) {
        return trace.entries().stream().filter(e -> e.stepId().equals(stepId)).toList();
    }
}
