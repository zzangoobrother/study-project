package com.example.myframework2.mvc.core.di.beans.factory.config;

import com.example.myframework2.mvc.core.di.beans.factory.support.InjectType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Set;

public interface BeanDefinition {
    Constructor<?> getInjectConstructor();
    Set<Field> getInjectFields();
    Class<?> getBeanClass();
    InjectType getResolvedInjectMode();
}
