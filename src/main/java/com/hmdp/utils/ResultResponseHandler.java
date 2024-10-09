package com.hmdp.utils;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.hmdp.dto.Result;

/**
 * @author Ernie Lee
 * */
@ControllerAdvice
public class ResultResponseHandler implements ResponseBodyAdvice<Result> {

    @Override
    public boolean supports(MethodParameter methodParameter, Class<? extends HttpMessageConverter<?>> aClass) {
        return Result.class.isAssignableFrom(methodParameter.getContainingClass());
    }

    @Override
    public Result beforeBodyWrite(Result result, MethodParameter methodParameter, MediaType mediaType, Class<? extends HttpMessageConverter<?>> aClass, ServerHttpRequest serverHttpRequest, ServerHttpResponse serverHttpResponse) {
        if (result != null) {
            if (result.getSuccess()) {
                serverHttpResponse.setStatusCode(HttpStatus.OK);
            } else {
                serverHttpResponse.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
        return result;
    }
}
