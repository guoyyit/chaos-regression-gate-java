package com.github.guoyyit.chaosregressiongatejava.gate.model;

import java.util.List;

/** Declarative scenario: request streams, seeds and dependency behaviour. */
public record Scenario(String name, List<Long> seeds, List<Step> steps,
                       List<DepFaultSpec> dependencyFaults) {
}
