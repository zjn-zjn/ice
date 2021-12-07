package com.ice.server.exception;

public interface IErrorCode {
    int getCode();

    String getFormatMsg(Object... params);
}
