package com.ice.server.exception;

import java.text.MessageFormat;

public enum ErrorCode implements IErrorCode {
    INTERNAL_ERROR(-1, "内部错误"),
    INPUT_ERROR(-2, "输入错误: {0}"),
    ID_NOT_EXIST(-3, "{0}:{1} 不存在"),
    ALREADY_EXIST(-5, "{0} 已存在"),
    CONF_NOT_FOUND(-6, "app:{0} {1}:{2} 配置未找到"),
    CONFIG_FILED_ILLEGAL(-14, "配置不合法: {0}"),
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
