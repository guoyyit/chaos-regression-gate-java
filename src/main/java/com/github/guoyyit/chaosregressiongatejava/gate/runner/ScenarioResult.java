package com.github.guoyyit.chaosregressiongatejava.gate.runner;

import com.github.guoyyit.chaosregressiongatejava.gate.model.Trace;

/** Outcome of one scenario run for one seed. */
public record ScenarioResult(String scenario, long seed, Trace trace, String failure) {

    public boolean passed() {
        return failure == null;
    }
}
