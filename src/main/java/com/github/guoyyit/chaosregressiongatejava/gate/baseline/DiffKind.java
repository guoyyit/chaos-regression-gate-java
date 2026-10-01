package com.github.guoyyit.chaosregressiongatejava.gate.baseline;

public enum DiffKind {
    NONE,
    /** Only volatile fields differ: not a regression. */
    VOLATILE_ONLY,
    /** Behaviour changed: a regression (or an explicitly expected change). */
    BEHAVIORAL
}
