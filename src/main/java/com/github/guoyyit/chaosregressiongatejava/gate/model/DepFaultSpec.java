package com.github.guoyyit.chaosregressiongatejava.gate.model;

import com.github.guoyyit.chaosregressiongatejava.gate.FaultType;

/** A fault applied at the dependency boundary for the Nth dependency call. */
public record DepFaultSpec(int callIndex, FaultType type) {
}
