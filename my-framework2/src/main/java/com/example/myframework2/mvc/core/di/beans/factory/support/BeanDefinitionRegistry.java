package com.example.myframework2.mvc.core.di.beans.factory.support;

import com.example.myframework2.mvc.core.di.beans.factory.config.BeanDefinition;

public interface BeanDefinitionRegistry {
    void registerBeanDefinition(Class<?> clazz, BeanDefinition beanDefinition);
}
