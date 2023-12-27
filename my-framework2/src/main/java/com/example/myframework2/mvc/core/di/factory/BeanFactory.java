package com.example.myframework2.mvc.core.di.factory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.springframework.beans.BeanUtils;

import javax.annotation.PostConstruct;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

        BeanDefinition beanDefinition = beanDefinitions.get(clazz);
        if (beanDefinition != null && beanDefinition instanceof AnnotatedBeanDefinition) {
            Optional<Object> optionalBean = createAnnotatedBean(beanDefinition);
            optionalBean.ifPresent(b -> beans.put(clazz, b));
            initialize(bean, clazz);
            return (T) optionalBean.orElse(null);
        }

        Optional<Class<?>> concreteClazz = BeanFactoryUtils.findConcreteClass(clazz, getBeanClasses());
        if (!concreteClazz.isPresent()) {
            return null;
        }

        beanDefinition = beanDefinitions.get(concreteClazz.get());
        bean = inject(beanDefinition);
        beans.put(concreteClazz.get(), bean);
        initialize(bean, concreteClazz.get());
        return (T) bean;
    }

    private Optional<Object> createAnnotatedBean(BeanDefinition beanDefinition) {
        AnnotatedBeanDefinition abd = (AnnotatedBeanDefinition) beanDefinition;
        Method method = abd.getMethod();
        Object[] args = populateArguments(method.getParameterTypes());
        return BeanFactoryUtils.invokeMethod(method, getBean(method.getDeclaringClass()), args);
    }

    private Object[] populateArguments(Class<?>[] paramTypes) {
        List<Object> args = Lists.newArrayList();
        for (Class<?> param : paramTypes) {
            Object bean = getBean(param);
            if (bean == null) {
                throw new NullPointerException();
            }

            args.add(getBean(param));
        }

        return args.toArray();
    }

    private void initialize(Object bean, Class<?> beanClass) {
        Set<Method> initializeMethods = BeanFactoryUtils.getBeanMethods(beanClass, PostConstruct.class);
        if (initializeMethods.isEmpty()) {
            return;
        }
        for (Method initializeMethod : initializeMethods) {
            BeanFactoryUtils.invokeMethod(initializeMethod, bean,
                    populateArguments(initializeMethod.getParameterTypes()));
        }
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
