package com.example.myframework2.mvc.core.di.factory;

import com.example.myframework2.mvc.core.annotation.Controller;
import com.example.myframework2.mvc.core.annotation.Repository;
import com.example.myframework2.mvc.core.annotation.Service;
import com.google.common.collect.Sets;
import org.reflections.Reflections;

import java.lang.annotation.Annotation;
import java.util.Set;

public class BeanScanner {
    private Reflections reflections;

    public BeanScanner(Object... basePackage) {
        this.reflections = new Reflections(basePackage);
    }

    public Set<Class<?>> scan() {
        return getTypesAnnotatedWith(Controller.class, Service.class, Repository.class);
    }

    private Set<Class<?>> getTypesAnnotatedWith(Class<? extends Annotation>... annotations) {
        Set<Class<?>> preInstantiateBeans = Sets.newHashSet();
        for (Class<? extends Annotation> annotation : annotations) {
            preInstantiateBeans.addAll(reflections.getTypesAnnotatedWith(annotation));
        }

        return preInstantiateBeans;
    }
}
