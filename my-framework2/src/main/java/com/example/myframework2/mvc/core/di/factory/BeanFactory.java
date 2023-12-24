package com.example.myframework2.mvc.core.di.factory;

import com.example.myframework2.mvc.core.annotation.Controller;
import com.google.common.collect.Maps;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BeanFactory {

    private Set<Class<?>> preInstanticateBeans;

    private Map<Class<?>, Object> beans = Maps.newHashMap();

    private List<Injector> injectors;

    public BeanFactory(Set<Class<?>> preInstanticateBeans) {
        this.preInstanticateBeans = preInstanticateBeans;
        this.injectors = Arrays.asList(new FieldInjector(this), new SetterInjector(this), new ConstructorInjector(this));
    }

    public <T> T getBean(Class<T> requiredType) {
        return (T) beans.get(requiredType);
    }

    public void registerBean(Class<?> clazz, Object bean) {
        beans.put(clazz, bean);
    }

    public Set<Class<?>> getPreInstanticateBeans() {
        return preInstanticateBeans;
    }

    public void initialize() {
        for (Class<?> clazz : preInstanticateBeans) {
            if (beans.get(clazz) == null) {
                inject(clazz);
            }
        }
    }

    private void inject(Class<?> clazz) {
        for (Injector injector : injectors) {
            injector.inject(clazz);
        }
    }

    public Map<Class<?>, Object> getControllers() {
        Map<Class<?>, Object> controllers = Maps.newHashMap();
        for (Class<?> clazz : preInstanticateBeans) {
            Annotation annotation = clazz.getAnnotation(Controller.class);
            if (annotation != null) {
                controllers.put(clazz, beans.get(clazz));
            }
        }

        return controllers;
    }

    public void clear() {
        preInstanticateBeans.clear();
        beans.clear();
    }
}
