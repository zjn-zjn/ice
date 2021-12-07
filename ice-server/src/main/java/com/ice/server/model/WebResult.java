package com.ice.server.model;

import com.ice.server.exception.ErrorCodeException;
import lombok.Data;

/**
 * @author zjn
 */
@Data
public class WebResult<T> {
    private int ret;
    private String msg;
    private T data;

    public WebResult() {
        this.ret = 0;
    }

    public WebResult(T data) {
        this.ret = 0;
        this.data = data;
    }

    public WebResult(int ret, String msg) {
        this.ret = ret;
        this.msg = msg;
    }

    public static <T> WebResult<T> fail(ErrorCodeException e) {
        return new WebResult<>(e.getErrorCode(), e.getMessage());
    }

    public static <T> WebResult<T> fail(int ret, String msg) {
        return new WebResult<>(ret, msg);
    }

    public static <T> WebResult<T> success(T data) {
        return new WebResult<>(0, null, data);
    }

    public WebResult(int ret, String msg, T data) {
        this.ret = ret;
        this.msg = msg;
        this.data = data;
    }
}
