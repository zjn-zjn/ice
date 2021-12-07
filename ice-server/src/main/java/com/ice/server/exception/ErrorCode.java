package com.ice.server.exception;

import java.text.MessageFormat;

public enum ErrorCode implements IErrorCode {
    INTERNAL_ERROR(-1, "Internal Error"),
    INPUT_ERROR(-2, "Input Error"),
    ID_NOT_EXIST(-3, "{0}:{1} not exist"),
    NOT_NULL(-4, "{0} can not be null"),
    ALREADY_EXIST(-5, "{0} already exist"),
    REMOTE_CONF_NOT_FOUND(-6, "app:{0} {1}:{2} remote conf not found remote {3}");
    private final int code;
    private final String msgTemplate;

    ErrorCode(int code, String msgTemplate) {
        this.code = code;
        this.msgTemplate = msgTemplate;
    }

    public int getCode() {
        return this.code;
    }

    public String getFormatMsg(Object... params) {
        return (new MessageFormat(this.msgTemplate)).format(params);
    }
}
