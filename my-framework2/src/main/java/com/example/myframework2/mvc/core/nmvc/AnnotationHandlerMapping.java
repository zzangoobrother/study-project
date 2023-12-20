package com.example.myframework2.mvc.core.nmvc;

import com.example.myframework2.mvc.core.annotation.Controller;
import com.example.myframework2.mvc.core.annotation.RequestMapping;
import com.example.myframework2.mvc.core.annotation.RequestMethod;
import com.google.common.collect.Maps;
import org.reflections.Reflections;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public class AnnotationHandlerMapping {
    private Object[] basePackage;

    private Map<HandlerKey, HandlerExecution> handlerExecutions = Maps.newHashMap();

    public AnnotationHandlerMapping(Object... basePackage) {
        this.basePackage = basePackage;
    }

    public void initialize() {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> clazzes = reflections.getTypesAnnotatedWith(Controller.class);

        clazzes.forEach(clazz -> {
            Arrays.stream(clazz.getDeclaredMethods()).forEach(method -> {
                RequestMapping requestMapping = method.getDeclaredAnnotation(RequestMapping.class);

                handlerExecutions.put(new HandlerKey(requestMapping.value(), requestMapping.method()),
                        new HandlerExecution(method, getInstantiate(clazz)));
            });
        });
    }

    private Object getInstantiate(Class<?> clazz) {
        try {
            return clazz.newInstance();
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public HandlerExecution getHandler(HttpServletRequest request) {
        String uri = request.getRequestURI();
        RequestMethod rm = RequestMethod.valueOf(request.getMethod());
        return handlerExecutions.get(new HandlerKey(uri, rm));
    }
}
