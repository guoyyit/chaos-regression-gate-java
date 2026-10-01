package com.github.guoyyit.chaosregressiongatejava.gate;

/** Fault kinds injectable at the client boundary and the dependency boundary. */
public enum FaultType {
    DELAY,
    TIMEOUT,
    CONNECTION_RESET,
    DUPLICATE,
    OUT_OF_ORDER,
    MIDWAY_FAILURE
}
