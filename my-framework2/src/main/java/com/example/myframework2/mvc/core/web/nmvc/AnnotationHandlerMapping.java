package com.example.myframework2.mvc.core.web.nmvc;

import com.example.myframework2.mvc.core.annotation.Controller;
import com.example.myframework2.mvc.core.annotation.RequestMapping;
import com.example.myframework2.mvc.core.annotation.RequestMethod;
import com.example.myframework2.mvc.core.di.context.ApplicationContext;
import com.example.myframework2.mvc.core.web.mvc.HandlerMapping;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.reflections.ReflectionUtils;

import javax.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

public class AnnotationHandlerMapping implements HandlerMapping {
    private ApplicationContext applicationContext;

    private Map<HandlerKey, HandlerExecution> handlerExecutions = Maps.newHashMap();

    public AnnotationHandlerMapping(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public void initialize() {
        Map<Class<?>, Object> controllers = getControllers(applicationContext);

        Set<Method> methods = getRequestMappingMethods(controllers.keySet());

        methods.forEach(method -> {
            RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
            handlerExecutions.put(createHandlerKey(requestMapping),
                    new HandlerExecution(method, controllers.get(method.getDeclaringClass())));
        });
    }

    public Map<Class<?>, Object> getControllers(ApplicationContext applicationContext) {
        Map<Class<?>, Object> controllers = Maps.newHashMap();
        for (Class<?> clazz : applicationContext.getBeanClasses()) {
            Annotation annotation = clazz.getAnnotation(Controller.class);
            if (annotation != null) {
                controllers.put(clazz, applicationContext.getBean(clazz));
            }
        }

        return controllers;
    }

    private Set<Method> getRequestMappingMethods(Set<Class<?>> controllers) {
        Set<Method> requestMappingMethods = Sets.newHashSet();
        for (Class<?> clazz : controllers) {
            requestMappingMethods.addAll(ReflectionUtils.getAllMethods(clazz, ReflectionUtils.withAnnotation(RequestMapping.class)));
        }

        return requestMappingMethods;
    }

    private HandlerKey createHandlerKey(RequestMapping rm) {
        return new HandlerKey(rm.value(), rm.method());
    }

    @Override
    public HandlerExecution getHandler(HttpServletRequest request) {
        String uri = request.getRequestURI();
        RequestMethod rm = RequestMethod.valueOf(request.getMethod());
        return handlerExecutions.get(new HandlerKey(uri, rm));
    }
}
