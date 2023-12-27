package com.example.myframework2.mvc.core.di.factory;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class AnnotationConfigApplicationContext implements ApplicationContext {
    private static final Logger log = LoggerFactory.getLogger(AnnotationConfigApplicationContext.class);
    private BeanFactory beanFactory;

    public AnnotationConfigApplicationContext(Class<?>... annotatedClasses) {
        Object[] basePackages = findBasePackages(annotatedClasses);
        this.beanFactory = new BeanFactory();
        AnnotatedBeanDefinitionReader abdr = new AnnotatedBeanDefinitionReader(beanFactory);
        abdr.register(annotatedClasses);

        if (basePackages.length > 0) {
            ClasspathBeanDefinitionScanner scanner = new ClasspathBeanDefinitionScanner(beanFactory);
            scanner.doScan(basePackages);
        }

        beanFactory.initialize();
    }

    private Object[] findBasePackages(Class<?>[] annotatedClasses) {
        List<Object> basePackages = Lists.newArrayList();
        for (Class<?> annotatedClass : annotatedClasses) {
            ComponentScan componentScan = annotatedClass.getAnnotation(ComponentScan.class);
            if (componentScan == null) {
                continue;
            }

            for (String basePackage : componentScan.value()) {
                log.info("Component Scan basePackage : {}", basePackage);
            }
            basePackages.addAll(Arrays.asList(componentScan.value()));
        }

        return basePackages.toArray();
    }

    @Override
    public <T> T getBean(Class<T> clazz) {
        return beanFactory.getBean(clazz);
    }

    @Override
    public Set<Class<?>> getBeanClasses() {
        return beanFactory.getBeanClasses();
    }
}
