package com.github.guoyyit.chaosregressiongatejava.gate.model;

import com.github.guoyyit.chaosregressiongatejava.gate.FaultType;

/** A fault attached to a scenario step (client boundary). */
public record FaultSpec(FaultType type, Integer delayMs) {
}
