package com.example.myframework2.mvc.core.di.context.annotation;

import com.example.myframework2.mvc.core.annotation.Bean;
import com.example.myframework2.mvc.core.di.beans.factory.support.DefaultBeanDefinition;
import com.example.myframework2.mvc.core.di.beans.factory.support.BeanFactoryUtils;
import com.example.myframework2.mvc.core.di.beans.factory.support.BeanDefinitionReader;
import com.example.myframework2.mvc.core.di.beans.factory.support.BeanDefinitionRegistry;

import java.lang.reflect.Method;
import java.util.Set;

public class AnnotatedBeanDefinitionReader implements BeanDefinitionReader {
    private BeanDefinitionRegistry beanDefinitionRegistry;

    public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry beanDefinitionRegistry) {
        this.beanDefinitionRegistry = beanDefinitionRegistry;
    }

    @Override
    public void loadBeanDefinitions(Class<?>... annotatedClasses) {
        for (Class<?> annotatedClass : annotatedClasses) {
            registerBean(annotatedClass);
        }
    }

    private void registerBean(Class<?> annotatedClass) {
        beanDefinitionRegistry.registerBeanDefinition(annotatedClass, new DefaultBeanDefinition(annotatedClass));
        Set<Method> beanMethods = BeanFactoryUtils.getBeanMethods(annotatedClass, Bean.class);
        for (Method beanMethod : beanMethods) {
            AnnotatedBeanDefinition abd = new AnnotatedBeanDefinition(beanMethod.getReturnType(), beanMethod);
            beanDefinitionRegistry.registerBeanDefinition(beanMethod.getReturnType(), abd);
        }
    }
}
