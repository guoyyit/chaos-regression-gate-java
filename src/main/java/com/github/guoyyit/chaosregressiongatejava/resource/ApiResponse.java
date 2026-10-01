package com.github.guoyyit.chaosregressiongatejava.resource;

/** Uniform API envelope: every endpoint returns a stable result code plus payload. */
public record ApiResponse(String code, String id, Long version, String data, String message) {

    public static ApiResponse of(ResultCode code, ResourceRecord record) {
        return new ApiResponse(code.name(), record.id(), record.version(), record.data(), null);
    }

    public static ApiResponse error(ResultCode code, String message) {
        return new ApiResponse(code.name(), null, null, null, message);
    }
}
