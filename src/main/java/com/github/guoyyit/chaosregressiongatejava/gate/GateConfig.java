package com.github.guoyyit.chaosregressiongatejava.gate;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Gate configuration, typically populated from system properties. */
public record GateConfig(Path scenariosDir, Path baselinesDir, Path runsDir,
                         int parallelism, long scenarioTimeoutSeconds,
                         String flakyPolicy, boolean updateBaseline,
                         String updateConfirm, List<String> expectedChanges) {

    /** Confirmation token required before baselines may be overwritten. */
    public static final String CONFIRM_TOKEN = "I_UNDERSTAND";

    public static GateConfig fromSystemProperties() {
        return new GateConfig(
                Path.of(System.getProperty("gate.scenariosDir", "src/test/resources/gate/scenarios")),
                Path.of(System.getProperty("gate.baselinesDir", "src/test/resources/gate/baselines")),
                Path.of(System.getProperty("gate.runsDir", "target/gate-runs")),
                Integer.getInteger("gate.parallelism", 4),
                Long.getLong("gate.scenarioTimeoutSeconds", 120L),
                System.getProperty("gate.flakyPolicy", "WARN"),
                Boolean.getBoolean("gate.updateBaseline"),
                System.getProperty("gate.updateConfirm", ""),
                List.copyOf(Arrays.asList(
                        System.getProperty("gate.expectedChanges", "").split(",")).stream()
                        .map(String::trim).filter(s -> !s.isEmpty()).toList()));
    }

    public boolean baselineUpdateConfirmed() {
        return updateBaseline && CONFIRM_TOKEN.equals(updateConfirm);
    }
}
