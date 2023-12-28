package com.example.myframework2.mvc.core.di.context.annotation;

import com.example.myframework2.mvc.core.di.beans.factory.support.DefaultBeanDefinition;

import java.lang.reflect.Method;

public class AnnotatedBeanDefinition extends DefaultBeanDefinition {
    private Method method;

    public AnnotatedBeanDefinition(Class<?> clazz, Method method) {
        super(clazz);
        this.method = method;
    }

    public Method getMethod() {
        return method;
    }
}
