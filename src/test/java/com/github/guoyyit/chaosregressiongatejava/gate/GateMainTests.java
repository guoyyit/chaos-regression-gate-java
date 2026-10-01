package com.github.guoyyit.chaosregressiongatejava.gate;

import com.github.guoyyit.chaosregressiongatejava.gate.trace.Trace;
import com.github.guoyyit.chaosregressiongatejava.gate.trace.TraceWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GateMainTests {

    private GateConfig config(Path scenarios, Path baselines, Path output) {
        GateConfig c = new GateConfig();
        c.setScenariosDir(scenarios.toString());
        c.setBaselinesDir(baselines.toString());
        c.setOutputDir(output.toString());
        c.setStabilityRuns(1);
        c.setParallelism(2);
        c.setScenarioTimeoutMs(30000);
        return c;
    }

    @Test
    void fullGateLifecycle(@TempDir Path tempDir) {
        Path scenarios = Path.of("src/test/resources/scenarios");
        Path baselines = tempDir.resolve("baselines");
        Path output = tempDir.resolve("output");
        baselines.toFile().mkdirs();

        // 1. Generate baselines (update-baseline)
        GateConfig updateConfig = config(scenarios, baselines, output);
        updateConfig.setUpdateBaseline(true);
        int code1 = new GateMain(updateConfig).run();
        assertEquals(0, code1, "baseline generation should pass");

        // 2. Run again -> MATCH
        GateConfig matchConfig = config(scenarios, baselines, output);
        int code2 = new GateMain(matchConfig).run();
        assertEquals(0, code2, "second run should match baselines");

        // 3. Tamper a baseline -> REGRESSION
        File baselineFile = baselines.resolve("basic-crud.json").toFile();
        TraceWriter writer = new TraceWriter();
        Trace baseline = writer.read(baselineFile);
        baseline.getRequests().get(0).getAttempts().get(0).setCode("STALE_VERSION");
        writer.write(baseline, baselineFile);

        GateConfig regressionConfig = config(scenarios, baselines, output);
        int code3 = new GateMain(regressionConfig).run();
        assertNotEquals(0, code3, "tampered baseline should cause regression");

        // 4. Minimal reproducer should be written
        File minimalDir = output.resolve("minimal").toFile();
        assertTrue(minimalDir.exists() && minimalDir.listFiles().length > 0,
                "minimal reproducers should be written");
    }

    @Test
    void noBaselinePassesWithWarning(@TempDir Path tempDir) {
        Path scenarios = Path.of("src/test/resources/scenarios");
        Path baselines = tempDir.resolve("baselines");
        Path output = tempDir.resolve("output");
        baselines.toFile().mkdirs();

        GateConfig config = config(scenarios, baselines, output);
        int code = new GateMain(config).run();
        // No baseline -> NO_BASELINE, not a failure
        assertEquals(0, code);
    }

    @Test
    void flakyPolicyFail(@TempDir Path tempDir) {
        Path scenarios = Path.of("src/test/resources/scenarios");
        Path baselines = tempDir.resolve("baselines");
        Path output = tempDir.resolve("output");
        baselines.toFile().mkdirs();

        GateConfig config = config(scenarios, baselines, output);
        config.setStabilityRuns(3);
        config.setFlakyPolicy("FAIL");
        int code = new GateMain(config).run();
        // Deterministic scenarios are stable -> no flakiness -> pass
        assertEquals(0, code);
    }
}
