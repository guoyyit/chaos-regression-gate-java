package com.github.guoyyit.chaosregressiongatejava.resource;

/**
 * Stable result codes returned by the resource service.
 */
public enum ResultCode {
    OK,
    CREATED,
    STALE_VERSION,
    NOT_FOUND,
    BAD_REQUEST,
    DEPENDENCY_FAILURE,
    CONNECTION_FAILURE,
    TIMEOUT
}
