package com.github.guoyyit.chaosregressiongatejava.gate.fault;

/**
 * Fault types supported by the gate.
 */
public enum FaultType {
    LATENCY,
    TIMEOUT,
    CONNECTION_RESET,
    DUPLICATE_DELIVERY,
    OUT_OF_ORDER,
    FAIL_MIDWAY
}
