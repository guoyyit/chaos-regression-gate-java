package com.github.guoyyit.chaosregressiongatejava.tests.gate;

import com.github.guoyyit.chaosregressiongatejava.gate.GateConfig;
import com.github.guoyyit.chaosregressiongatejava.gate.report.GateReport;
import com.github.guoyyit.chaosregressiongatejava.gate.runner.GateRunner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Build integration: runs every scenario in gate.scenariosDir against its
 *  baseline and fails the build on regression or stable failure. The handling
 *  of flaky-only results is configurable via -Dgate.flakyPolicy=FAIL|WARN|IGNORE. */
class RegressionGateBuildTest {

    @Test
    void regressionGate() {
        GateConfig config = GateConfig.fromSystemProperties();
        GateReport report = new GateRunner(config).runAll();
        for (GateReport.Entry entry : report.entries()) {
            System.out.printf("[gate] %-32s %-16s %s%n", entry.scenario(), entry.status(),
                    entry.detail() == null ? "" : entry.detail());
        }
        int exitCode = report.exitCode(config.flakyPolicy());
        assertEquals(0, exitCode,
                "regression gate failed with exit code " + exitCode + "; see "
                        + config.runsDir().resolve("gate-report.md"));
    }
}
