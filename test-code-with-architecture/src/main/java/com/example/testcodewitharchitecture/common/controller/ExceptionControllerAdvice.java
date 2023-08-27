package com.example.testcodewitharchitecture.common.controller;

import com.example.testcodewitharchitecture.common.domain.exception.CertificationCodeNotMatchedException;
import com.example.testcodewitharchitecture.common.domain.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ExceptionControllerAdvice {

    @ResponseBody
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(ResourceNotFoundException.class)
    public String resourceNotFoundException(ResourceNotFoundException exception) {
        return exception.getMessage();
    }

    @ResponseBody
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(CertificationCodeNotMatchedException.class)
    public String certificationCodeNotMatchedException(CertificationCodeNotMatchedException exception) {
        return exception.getMessage();
    }
}
