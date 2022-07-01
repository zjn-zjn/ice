package com.ice.server.controller.common;

import com.ice.server.controller.IceAppController;
import com.ice.server.controller.IceBaseController;
import com.ice.server.controller.IceConfController;
import com.ice.server.controller.IceMockController;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import com.ice.server.model.WebResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * @author waitmoon
 */
@Slf4j
@RestControllerAdvice(basePackageClasses = {IceConfController.class, IceBaseController.class, IceAppController.class, IceMockController.class})
public class WebAbstractResponseAdapter implements ResponseBodyAdvice<Object> {

    @ExceptionHandler(value = Throwable.class)
    public Object exceptionHandler(Exception e) {
        log.error("error: ", e);
        if (e instanceof ErrorCodeException) {
            ErrorCodeException exception = (ErrorCodeException) e;
            return WebResult.fail(exception);
        }
        return WebResult.fail(ErrorCode.INTERNAL_ERROR.getCode(), e.getMessage());
    }

    @Override
    public boolean supports(MethodParameter methodParameter,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object o,
                                  MethodParameter methodParameter,
                                  MediaType mediaType,
                                  Class<? extends HttpMessageConverter<?>> aClass,
                                  ServerHttpRequest serverHttpRequest,
                                  ServerHttpResponse serverHttpResponse) {

        if (o instanceof WebResult) {
            return o;
        }
        return WebResult.success(o);
    }
}
