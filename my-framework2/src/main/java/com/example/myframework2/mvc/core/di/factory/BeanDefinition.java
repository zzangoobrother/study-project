package com.example.myframework2.mvc.core.di.factory;

import com.google.common.collect.Sets;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

public class BeanDefinition {

    private Class<?> beanClazz;

    private Constructor<?> injectConstructor;

    private Set<Field> injectFields;

    public BeanDefinition(Class<?> clazz) {
        this.beanClazz = clazz;
        injectConstructor = getInjectConstructor(clazz);
        injectFields = getInjectFields(clazz, injectConstructor);
    }

    private Constructor<?> getInjectConstructor(Class<?> clazz) {
        return BeanFactoryUtils.getInjectedConstructor(clazz);
    }

    private Set<Field> getInjectFields(Class<?> clazz, Constructor<?> injectConstructor) {
        if (injectConstructor != null) {
            return Sets.newHashSet();
        }

        Set<Field> injectFields = Sets.newHashSet();
        Set<Class<?>> injectProperties =  getInjectPropertiesType(clazz);
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if (injectProperties.contains(field.getType())) {
                injectFields.add(field);
            }
        }

        return injectFields;
    }

    private Set<Class<?>> getInjectPropertiesType(Class<?> clazz) {
        Set<Class<?>> injectProperties = Sets.newHashSet();
        Set<Method> injectMethod = BeanFactoryUtils.getInjectedMethod(clazz);
        for (Method method : injectMethod) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != 1) {
                throw new IllegalStateException();
            }

            injectProperties.add(parameterTypes[0]);
        }

        Set<Field> injectedFields = BeanFactoryUtils.getInjectedFields(clazz);
        for (Field field : injectedFields) {
            injectProperties.add(field.getType());
        }

        return injectProperties;
    }

    public Constructor<?> getInjectConstructor() {
        return injectConstructor;
    }

    public Set<Field> getInjectFields() {
        return injectFields;
    }

    public Class<?> getBeanClazz() {
        return beanClazz;
    }

    public InjectType getResolvedInjectMode() {
        if (injectConstructor != null) {
            return InjectType.INJECT_CONSTRUCTOR;
        }

        if (!injectFields.isEmpty()) {
            return InjectType.INJECT_FIELD;
        }

        return InjectType.INJECT_NO;
    }
}
