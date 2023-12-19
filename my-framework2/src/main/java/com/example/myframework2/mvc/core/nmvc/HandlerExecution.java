package com.example.myframework2.mvc.core.nmvc;

import com.example.myframework2.mvc.core.mvc.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class HandlerExecution {

    private final Method method;
    private final Object instantiate;

    public HandlerExecution(Method method, Object instantiate) {
        this.method = method;
        this.instantiate = instantiate;
    }

    public ModelAndView handle(HttpServletRequest request, HttpServletResponse response) {

        try {
            return (ModelAndView) method.invoke(instantiate, request, response);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
