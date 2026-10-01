package com.github.guoyyit.chaosregressiongatejava.gate.model;

import java.util.List;

/** One declarative request in a scenario. Steps sharing a stream run in order;
 *  different streams are interleaved deterministically by the seeded scheduler. */
public record Step(String id, String stream, String method, String path, String body,
                   List<FaultSpec> faults) {
}
