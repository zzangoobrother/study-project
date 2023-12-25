package com.example.myframework2.mvc.core.di.factory;

public interface BeanDefinitionRegistry {
    void registerBeanDefinition(Class<?> clazz, BeanDefinition beanDefinition);
}
