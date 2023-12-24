package com.example.myframework2.mvc.core.di.factory;

import com.google.common.collect.Lists;
import org.springframework.beans.BeanUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

public class SetterInjector implements Injector {

    private BeanFactory beanFactory;

    public SetterInjector(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public void inject(Class<?> clazz) {
        instantiateClass(clazz);
        Set<?> injectedMethods = BeanFactoryUtils.getInjectedMethod(clazz);

        for (Object injectedBean : injectedMethods) {
            Method method = (Method) injectedBean;
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != 1) {
                throw new IllegalStateException();
            }

            Object bean = instantiateClass(parameterTypes[0]);

            try {
                method.invoke(beanFactory.getBean(method.getDeclaringClass()), bean);
            } catch (InvocationTargetException | IllegalAccessException e) {

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
