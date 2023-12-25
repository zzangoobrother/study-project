package com.example.myframework2.mvc.core.di.factory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.springframework.beans.BeanUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BeanFactory implements BeanDefinitionRegistry {

    private Map<Class<?>, Object> beans = Maps.newHashMap();

    private Map<Class<?>, BeanDefinition> beanDefinitions = Maps.newHashMap();

    public void registerBean(Class<?> clazz, Object bean) {
        beans.put(clazz, bean);
    }

    public Set<Class<?>> getBeanClasses() {
        return beanDefinitions.keySet();
    }

    public void initialize() {
        for (Class<?> clazz : beanDefinitions.keySet()) {
            getBean(clazz);
        }
    }

    public <T> T getBean(Class<T> clazz) {
        Object bean = beans.get(clazz);
        if (bean != null) {
            return (T) bean;
        }

        Class<?> concreteClass = findConcreteClass(clazz);
        BeanDefinition beanDefinition = beanDefinitions.get(concreteClass);
        bean = inject(beanDefinition);
        beans.put(concreteClass, bean);
        return (T) bean;
    }

    private Class<?> findConcreteClass(Class<?> clazz) {
        Set<Class<?>> beanClasses = getBeanClasses();
        Class<?> concreteClazz = BeanFactoryUtils.findConcreteClass(clazz, beanClasses);
        if (!beanClasses.contains(concreteClazz)) {
            throw new IllegalStateException(clazz + "는 Bean이 아니다.");
        }

        return concreteClazz;
    }

    private Object inject(BeanDefinition beanDefinition) {
        if (beanDefinition.getResolvedInjectMode() == InjectType.INJECT_NO) {
            return BeanUtils.instantiateClass(beanDefinition.getBeanClazz());
        } else if (beanDefinition.getResolvedInjectMode() == InjectType.INJECT_FIELD) {
            return injectFields(beanDefinition);
        } else {
            return injectConstructor(beanDefinition);
        }
    }

    private Object injectFields(BeanDefinition beanDefinition) {
        Object bean = BeanUtils.instantiateClass(beanDefinition.getBeanClazz());
        Set<Field> injectFields = beanDefinition.getInjectFields();
        for (Field field : injectFields) {
            injectFields(bean, field);
        }

        return bean;
    }

    private void injectFields(Object bean, Field field) {
        try {
            field.setAccessible(true);
            field.set(bean, getBean(field.getType()));
        } catch (IllegalAccessException e) {

        }
    }

    private Object injectConstructor(BeanDefinition beanDefinition) {
        Constructor<?> constructor = beanDefinition.getInjectConstructor();
        List<Object> args = Lists.newArrayList();
        for (Class<?> clazz : constructor.getParameterTypes()) {
            args.add(getBean(clazz));
        }

        return BeanUtils.instantiateClass(constructor, args.toArray());
    }

    public void clear() {
        beans.clear();
    }

    @Override
    public void registerBeanDefinition(Class<?> clazz, BeanDefinition beanDefinition) {
        beanDefinitions.put(clazz, beanDefinition);
    }
}
