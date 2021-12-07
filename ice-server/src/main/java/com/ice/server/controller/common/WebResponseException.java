package com.ice.server.controller.common;

import com.ice.server.controller.AppController;
import com.ice.server.controller.BaseController;
import com.ice.server.controller.ConfController;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import com.ice.server.model.WebResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(basePackageClasses = {ConfController.class, BaseController.class, AppController.class})
public class WebResponseException extends WebAbstractResponseAdapter {

    @ExceptionHandler(value = Throwable.class)
    public Object exceptionHandler(Exception e) {
        log.error("error: " + e.getMessage());
        if (e instanceof ErrorCodeException) {
            ErrorCodeException exception = (ErrorCodeException) e;
            return WebResult.fail(exception);
        }
        return WebResult.fail(ErrorCode.INTERNAL_ERROR.getCode(), e.getMessage());
    }
}
