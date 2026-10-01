package com.github.guoyyit.chaosregressiongatejava.gate.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Writes gate reports to disk as JSON and human-readable text.
 */
public class ReportWriter {
    private final ObjectMapper mapper;

    public ReportWriter() {
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void write(GateReport report, File file) {
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            mapper.writeValue(file, report);
        } catch (IOException e) {
            throw new IllegalStateException("failed to write report to " + file, e);
        }
    }

    public String toText(GateReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Chaos Regression Gate Report ===\n");
        sb.append("Verdict: ").append(report.getVerdict()).append("\n");
        sb.append(String.format("Total: %d  Passed: %d  Regressions: %d  Fluctuations: %d  ExpectedChanges: %d  StableFailures: %d  FlakyFailures: %d%n",
                report.getTotal(), report.getPassed(), report.getRegressions(), report.getFluctuations(),
                report.getExpectedChanges(), report.getStableFailures(), report.getFlakyFailures()));
        sb.append("\n");
        for (ScenarioReport sr : report.getScenarios()) {
            sb.append(String.format("  [%s] %s%n", sr.getVerdict(), sr.getName()));
            if (sr.isFlaky()) {
                sb.append("      (flaky: traces differ across stability runs)\n");
            }
            if (sr.getComparison() != null && !sr.getComparison().getDiffs().isEmpty()) {
                sr.getComparison().getDiffs().forEach(d ->
                        sb.append(String.format("      %s:%s expected=%s actual=%s%s%n",
                                d.getRequestId(), d.getField(), d.getExpected(), d.getActual(),
                                d.isFluctuationOnly() ? " (fluctuation)" : "")));
            }
            if (sr.getRunResult() != null && !sr.getRunResult().isSuccess()) {
                sb.append("      failure: ").append(sr.getRunResult().getFailureReason()).append("\n");
            }
            if (sr.getMinimalScenarioPath() != null) {
                sb.append("      minimal reproducer: ").append(sr.getMinimalScenarioPath()).append("\n");
            }
        }
        return sb.toString();
    }
}
