package com.example.myframework2.mvc.core.di.factory;

import com.google.common.collect.Maps;

import java.util.Map;
import java.util.Set;

public class BeanFactory {

    private Set<Class<?>> preInstanticateBeans;

    private Map<Class<?>, Object> beans = Maps.newHashMap();

    public BeanFactory(Set<Class<?>> preInstanticateBeans) {
        this.preInstanticateBeans = preInstanticateBeans;
    }

    public <T> T getBean(Class<T> requiredType) {
        return (T) beans.get(requiredType);
    }

    public void initialize() {

    }
}
