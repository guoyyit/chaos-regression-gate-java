package com.github.guoyyit.chaosregressiongatejava.gate;

import com.github.guoyyit.chaosregressiongatejava.gate.harness.DependencyStubServer;
import com.github.guoyyit.chaosregressiongatejava.gate.harness.ServerHarness;
import com.github.guoyyit.chaosregressiongatejava.gate.model.Scenario;
import com.github.guoyyit.chaosregressiongatejava.gate.report.GateReport;
import com.github.guoyyit.chaosregressiongatejava.gate.runner.GateRunner;
import com.github.guoyyit.chaosregressiongatejava.gate.runner.ScenarioRunner;
import java.nio.file.Path;

/** CLI entry point: run scenarios, replay a single (possibly shrunk) scenario,
 *  or update baselines with explicit confirmation. */
public class GateMain {

    public static void main(String[] args) {
        String command = args.length == 0 ? "run" : args[0];
        GateConfig config = GateConfig.fromSystemProperties();
        int exitCode;
        switch (command) {
            case "run" -> exitCode = run(config);
            case "replay" -> exitCode = replay(config, args);
            case "update-baseline" -> exitCode = updateBaseline(config);
            default -> {
                System.err.println("unknown command: " + command
                        + " (expected run | replay <scenario.json> | update-baseline)");
                exitCode = 64;
            }
        }
        System.exit(exitCode);
    }

    private static int run(GateConfig config) {
        GateReport report = new GateRunner(config).runAll();
        print(report);
        return report.exitCode(config.flakyPolicy());
    }

    private static int updateBaseline(GateConfig config) {
        if (!config.baselineUpdateConfirmed()) {
            System.err.println("refusing to update baselines without explicit confirmation: "
                    + "-Dgate.updateBaseline=true -Dgate.updateConfirm=" + GateConfig.CONFIRM_TOKEN);
            return 64;
        }
        GateReport report = new GateRunner(config).runAll();
        print(report);
        return report.hasRegression() ? 1 : 0;
    }

    /** Replays a single scenario file (e.g. a shrunk minimal repro) and prints its trace. */
    private static int replay(GateConfig config, String[] args) {
        if (args.length < 2) {
            System.err.println("usage: replay <scenario.json>");
            return 64;
        }
        Scenario scenario = new ScenarioLoader().load(Path.of(args[1]));
        Path runDir = config.runsDir().resolve(scenario.name() + "-replay");
        try (DependencyStubServer dependency = DependencyStubServer.start();
             ServerHarness server = ServerHarness.start(dependency.port())) {
            String baseUrl = "http://127.0.0.1:" + server.port();
            ScenarioRunner runner = new ScenarioRunner(runDir);
            for (long seed : scenario.seeds()) {
                runner.run(scenario, seed, baseUrl);
                System.out.println("trace written under " + runDir.resolve("traces"));
            }
            return 0;
        } catch (Exception e) {
            System.err.println("replay failed: " + e);
            return 1;
        }
    }

    private static void print(GateReport report) {
        for (GateReport.Entry entry : report.entries()) {
            System.out.printf("%-40s %-16s %s%n", entry.scenario(), entry.status(),
                    entry.detail() == null ? "" : entry.detail());
        }
        System.out.println("gate result: " + (report.hasRegression() ? "FAILED"
                : report.hasFlaky() ? "FLAKY" : "PASSED"));
    }
}
