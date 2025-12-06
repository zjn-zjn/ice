package com.ice.server.exception;

public class ErrorCodeException extends RuntimeException {
    private final IErrorCode errorCode;

    public ErrorCodeException(IErrorCode errorCode, Object... params) {
        super(errorCode.getFormatMsg(params));
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return this.errorCode.getCode();
    }
}
