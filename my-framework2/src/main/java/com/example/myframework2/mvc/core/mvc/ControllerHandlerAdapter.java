package com.example.myframework2.mvc.core.mvc;

import com.example.myframework2.mvc.core.mvc.Controller;
import com.example.myframework2.mvc.core.mvc.ModelAndView;
import com.example.myframework2.mvc.core.nmvc.HandlerAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ControllerHandlerAdapter implements HandlerAdapter {
    @Override
    public boolean supports(Object handler) {
        return handler instanceof Controller;
    }

    @Override
    public ModelAndView handle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        return ((Controller) handler).execute(request, response);
    }
}
