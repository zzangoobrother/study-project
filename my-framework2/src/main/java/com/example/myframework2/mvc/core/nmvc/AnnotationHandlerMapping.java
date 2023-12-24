package com.example.myframework2.mvc.core.nmvc;

import com.example.myframework2.mvc.core.annotation.RequestMapping;
import com.example.myframework2.mvc.core.annotation.RequestMethod;
import com.example.myframework2.mvc.core.di.factory.BeanFactory;
import com.example.myframework2.mvc.core.di.factory.BeanScanner;
import com.example.myframework2.mvc.core.mvc.HandlerMapping;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.reflections.ReflectionUtils;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

public class AnnotationHandlerMapping implements HandlerMapping {
    private Object[] basePackage;

    private Map<HandlerKey, HandlerExecution> handlerExecutions = Maps.newHashMap();

    public AnnotationHandlerMapping(Object... basePackage) {
        this.basePackage = basePackage;
    }

    public void initialize() {
        BeanScanner scanner = new BeanScanner(basePackage);
        BeanFactory beanFactory = new BeanFactory(scanner.scan());
        beanFactory.initialize();
        Map<Class<?>, Object> controllers = beanFactory.getControllers();

        Set<Method> methods = getRequestMappingMethods(controllers.keySet());

        methods.forEach(method -> {
            RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
            handlerExecutions.put(createHandlerKey(requestMapping),
                    new HandlerExecution(method, controllers.get(method.getDeclaringClass())));
        });
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
