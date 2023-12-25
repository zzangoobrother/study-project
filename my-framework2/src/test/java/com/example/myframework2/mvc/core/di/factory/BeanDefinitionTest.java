package com.example.myframework2.mvc.core.di.factory;

import com.example.myframework2.mvc.core.di.factory.example.JdbcUserRepository;
import com.example.myframework2.mvc.core.di.factory.example.MyQnaService;
import com.example.myframework2.mvc.core.di.factory.example.MyUserController;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeanDefinitionTest {
    private Logger log = LoggerFactory.getLogger(BeanDefinitionTest.class);

    @Test
    void getResolvedAutowireMode() {
        BeanDefinition beanDefinition = new BeanDefinition(JdbcUserRepository.class);
        assertEquals(InjectType.INJECT_NO, beanDefinition.getResolvedInjectMode());

        beanDefinition = new BeanDefinition(MyUserController.class);
        assertEquals(InjectType.INJECT_FIELD, beanDefinition.getResolvedInjectMode());

        beanDefinition = new BeanDefinition(MyQnaService.class);
        assertEquals(InjectType.INJECT_CONSTRUCTOR, beanDefinition.getResolvedInjectMode());
    }

    @Test
    void getInjectProperties() {
        BeanDefinition beanDefinition = new BeanDefinition(MyUserController.class);
        Set<Field> injectFields = beanDefinition.getInjectFields();
        for (Field field : injectFields) {
            log.debug("inject field : {}", field);
        }
    }

    @Test
    void getConstorllers() {
        BeanDefinition beanDefinition = new BeanDefinition(MyQnaService.class);
        Set<Field> injectFields = beanDefinition.getInjectFields();
        assertEquals(0, injectFields.size());
        Constructor<?> constructor = beanDefinition.getInjectConstructor();
        log.debug("inject constructor : {}", constructor);
    }
}
