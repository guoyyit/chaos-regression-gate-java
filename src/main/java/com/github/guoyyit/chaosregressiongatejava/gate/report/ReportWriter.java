package com.github.guoyyit.chaosregressiongatejava.gate.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes a human-readable markdown gate report. */
public class ReportWriter {

    public void write(GateReport report, Path file) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Chaos Regression Gate Report\n\n");
        sb.append("| Scenario | Status | Detail |\n");
        sb.append("|----------|--------|--------|\n");
        for (GateReport.Entry entry : report.entries()) {
            sb.append("| ").append(entry.scenario())
                    .append(" | ").append(entry.status())
                    .append(" | ").append(entry.detail() == null ? "" : entry.detail().replace("\n", "<br>"))
                    .append(" |\n");
        }
        sb.append("\nOverall: ").append(report.hasRegression() ? "FAILED (regression or stable failure)"
                : report.hasFlaky() ? "FLAKY" : "PASSED").append('\n');
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, sb.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write report " + file, e);
        }
    }
}
