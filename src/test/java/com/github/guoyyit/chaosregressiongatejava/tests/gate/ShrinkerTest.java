package com.github.guoyyit.chaosregressiongatejava.tests.gate;

import com.github.guoyyit.chaosregressiongatejava.gate.ScenarioLoader;
import com.github.guoyyit.chaosregressiongatejava.gate.harness.DependencyStubServer;
import com.github.guoyyit.chaosregressiongatejava.gate.harness.ServerHarness;
import com.github.guoyyit.chaosregressiongatejava.gate.model.Scenario;
import com.github.guoyyit.chaosregressiongatejava.gate.model.Step;
import com.github.guoyyit.chaosregressiongatejava.gate.model.Trace;
import com.github.guoyyit.chaosregressiongatejava.gate.runner.ScenarioRunner;
import com.github.guoyyit.chaosregressiongatejava.gate.shrink.Shrinker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shrinker must reduce a failing scenario to a minimal repro that still
 *  fails and can be replayed directly from disk. */
class ShrinkerTest {

    @TempDir
    Path runDir;

    private Scenario failingScenario() {
        return new Scenario("shrink-me", List.of(5L), List.of(
                new Step("create", "main", "POST", "/resources/a", "{\"data\":\"1\"}", List.of()),
                new Step("read-irrelevant", "main", "GET", "/resources/a", null, List.of()),
                new Step("create-again", "main", "POST", "/resources/a", "{\"data\":\"2\"}", List.of()),
                new Step("read-missing", "main", "GET", "/resources/never-created", null, List.of()),
                new Step("create-other", "main", "POST", "/resources/b", "{\"data\":\"3\"}", List.of())),
                List.of());
    }

    @Test
    void shrinksToMinimalReproThatStillFails() {
        Scenario scenario = failingScenario();
        Shrinker shrinker = new Shrinker();
        try (DependencyStubServer dependency = DependencyStubServer.start();
             ServerHarness server = ServerHarness.start(dependency.port())) {
            String baseUrl = "http://127.0.0.1:" + server.port();
            ScenarioRunner runner = new ScenarioRunner(runDir);
            Predicate<Scenario> stillFails = candidate -> {
                Trace trace = runner.run(candidate, 5L, baseUrl);
                return trace.entries().stream().anyMatch(e -> "ALREADY_EXISTS".equals(e.code()));
            };
            assertTrue(stillFails.test(scenario), "precondition: original scenario fails");

            Scenario shrunk = shrinker.shrink(scenario, stillFails);

            assertEquals(2, shrunk.steps().size(),
                    "only the create and the conflicting re-create are needed");
            assertEquals(List.of("create", "create-again"),
                    shrunk.steps().stream().map(Step::id).toList());
            assertTrue(stillFails.test(shrunk), "minimal scenario must still reproduce the failure");

            Path persisted = shrinker.persist(shrunk, runDir.resolve("shrunk"));
            assertTrue(Files.exists(persisted));
            Scenario reloaded = new ScenarioLoader().load(persisted);
            assertTrue(stillFails.test(reloaded), "persisted minimal scenario must replay directly");
        }
    }
}
