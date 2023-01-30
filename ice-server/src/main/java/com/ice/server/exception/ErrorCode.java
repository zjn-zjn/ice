package com.ice.server.exception;

import java.text.MessageFormat;

public enum ErrorCode implements IErrorCode {
    INTERNAL_ERROR(-1, "Internal Error"),
    INPUT_ERROR(-2, "Input Error: {0}"),
    ID_NOT_EXIST(-3, "{0}:{1} not exist"),
    CAN_NOT_NULL(-4, "{0} can not be null"),
    ALREADY_EXIST(-5, "{0} already exist"),
    REMOTE_CONF_NOT_FOUND(-6, "app:{0} {1}:{2} remote conf not found remote {3}"),
    CONF_NOT_FOUND(-6, "app:{0} {1}:{2} conf not found"),
    REMOTE_ERROR(-7, "Remote Error app:{0} {1}"),
    NO_AVAILABLE_CLIENT(-8, "no available client app:{0}"),
    CLIENT_NOT_AVAILABLE(-9, "client not available app:{0} address:{1}"),
    REMOTE_RUN_ERROR(-10, "client run error app:{0} address:{1}"),
    CLIENT_CLOSED(-11, "client closed"),
    TIMEOUT(-12, "time out"),
    CLIENT_CLASS_NOT_FOUND(-13, "class:{0} type:{1} not found in any available client with app:{2}"),

    CONFIG_FILED_ILLEGAL(-14, "config illegal {0}"),
    CUSTOM(-255, "{0}");
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
