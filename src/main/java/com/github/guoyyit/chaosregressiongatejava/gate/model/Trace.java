package com.github.guoyyit.chaosregressiongatejava.gate.model;

import java.util.List;

/** A full normalized execution trace. */
public record Trace(String scenario, long seed, List<TraceEntry> entries) {
}
