package com.example.myframework2.mvc.core.di.factory;

import com.google.common.collect.Lists;
import org.springframework.beans.BeanUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

public class FieldInjector implements Injector {

    private BeanFactory beanFactory;

    public FieldInjector(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public void inject(Class<?> clazz) {
        instantiateClass(clazz);
        Set<?> injectedFields = BeanFactoryUtils.getInjectedFields(clazz);

        for (Object injectedBean : injectedFields) {
            Field field = (Field) injectedBean;
            Class<?> concreteClazz = field.getType();
            Object bean = beanFactory.getBean(concreteClazz);
            if (bean == null) {
                bean = instantiateClass(concreteClazz);
            }

            try {
                field.setAccessible(true);
                field.set(beanFactory.getBean(field.getDeclaringClass()), bean);
            } catch (IllegalAccessException | IllegalArgumentException e) {

            }
        }
    }

    private Object instantiateClass(Class<?> clazz) {
        Class<?> concreateClass = findBeanClass(clazz, beanFactory.getPreInstanticateBeans());
        Object bean = beanFactory.getBean(concreateClass);
        if (bean != null) {
            return bean;
        }

        Constructor<?> injectedConstructor = BeanFactoryUtils.getInjectedConstructor(concreateClass);
        if (injectedConstructor == null) {
            bean = BeanUtils.instantiateClass(concreateClass);
            beanFactory.registerBean(concreateClass, bean);
            return bean;
        }

        bean = instantiateConstructor(injectedConstructor);
        beanFactory.registerBean(concreateClass, bean);
        return bean;
    }

    private Object instantiateConstructor(Constructor<?> constructor) {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        List<Object> args = Lists.newArrayList();
        for (Class<?> clazz : parameterTypes) {
            Class<?> concreteClazz = findBeanClass(clazz, beanFactory.getPreInstanticateBeans());
            Object bean = beanFactory.getBean(concreteClazz);
            if (bean == null) {
                bean = instantiateClass(concreteClazz);
            }
            args.add(bean);
        }

        return BeanUtils.instantiateClass(constructor, args.toArray());
    }

    private Class<?> findBeanClass(Class<?> clazz, Set<Class<?>> preInstanticateBeans) {
        Class<?> concreteClazz = BeanFactoryUtils.findConcreteClass(clazz, preInstanticateBeans);
        if (!preInstanticateBeans.contains(concreteClazz)) {
            throw new IllegalStateException();
        }

        return concreteClazz;
    }
}
