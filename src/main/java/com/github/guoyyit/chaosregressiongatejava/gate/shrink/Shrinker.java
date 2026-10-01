package com.github.guoyyit.chaosregressiongatejava.gate.shrink;

import com.github.guoyyit.chaosregressiongatejava.gate.ScenarioLoader;
import com.github.guoyyit.chaosregressiongatejava.gate.model.DepFaultSpec;
import com.github.guoyyit.chaosregressiongatejava.gate.model.FaultSpec;
import com.github.guoyyit.chaosregressiongatejava.gate.model.Scenario;
import com.github.guoyyit.chaosregressiongatejava.gate.model.Step;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Shrinks a failing scenario by greedily removing steps, step faults and
 *  dependency faults while the failure still reproduces. The result is
 *  persisted as a standalone scenario file that can be replayed directly. */
public class Shrinker {

    private final ScenarioLoader loader = new ScenarioLoader();

    /** Shrinks the scenario; the predicate returns true if the failure still reproduces. */
    public Scenario shrink(Scenario scenario, Predicate<Scenario> failureStillReproduces) {
        Scenario current = scenario;
        boolean changed = true;
        while (changed) {
            changed = false;

            List<Step> steps = new ArrayList<>(current.steps());
            for (int i = 0; i < steps.size(); i++) {
                List<Step> candidate = new ArrayList<>(steps);
                candidate.remove(i);
                Scenario attempt = withSteps(current, candidate);
                if (failureStillReproduces.test(attempt)) {
                    current = attempt;
                    steps = candidate;
                    changed = true;
                    i--;
                }
            }

            for (int i = 0; i < steps.size(); i++) {
                Step step = steps.get(i);
                if (step.faults() == null || step.faults().isEmpty()) {
                    continue;
                }
                List<FaultSpec> faults = new ArrayList<>(step.faults());
                for (int j = 0; j < faults.size(); j++) {
                    List<FaultSpec> candidateFaults = new ArrayList<>(faults);
                    candidateFaults.remove(j);
                    List<Step> candidateSteps = new ArrayList<>(steps);
                    candidateSteps.set(i, new Step(step.id(), step.stream(), step.method(),
                            step.path(), step.body(), candidateFaults));
                    Scenario attempt = withSteps(current, candidateSteps);
                    if (failureStillReproduces.test(attempt)) {
                        current = attempt;
                        steps = candidateSteps;
                        faults = candidateFaults;
                        changed = true;
                        j--;
                    }
                }
            }

            List<DepFaultSpec> depFaults = new ArrayList<>(
                    current.dependencyFaults() == null ? List.<DepFaultSpec>of() : current.dependencyFaults());
            for (int i = 0; i < depFaults.size(); i++) {
                List<DepFaultSpec> candidate = new ArrayList<>(depFaults);
                candidate.remove(i);
                Scenario attempt = new Scenario(current.name(), current.seeds(), current.steps(),
                        candidate);
                if (failureStillReproduces.test(attempt)) {
                    current = attempt;
                    depFaults = candidate;
                    changed = true;
                    i--;
                }
            }
        }
        return current;
    }

    private Scenario withSteps(Scenario scenario, List<Step> steps) {
        return new Scenario(scenario.name(), scenario.seeds(), steps, scenario.dependencyFaults());
    }

    /** Persists the minimal scenario so it can be replayed directly. */
    public Path persist(Scenario shrunk, Path outputDir) {
        Path file = outputDir.resolve(shrunk.name() + ".min.json");
        loader.save(shrunk, file);
        return file;
    }
}
