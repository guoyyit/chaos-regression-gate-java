package com.github.guoyyit.chaosregressiongatejava.resource;

public class ApiException extends RuntimeException {
    private final ResultCode code;

    public ApiException(ResultCode code, String message) {
        super(message);
        this.code = code;
    }

    public ResultCode code() {
        return code;
    }
}
