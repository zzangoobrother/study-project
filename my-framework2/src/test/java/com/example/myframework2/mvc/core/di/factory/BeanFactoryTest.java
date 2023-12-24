package com.example.myframework2.mvc.core.di.factory;

import com.example.myframework2.mvc.core.di.factory.example.MyQnaService;
import com.example.myframework2.mvc.core.di.factory.example.MyUserController;
import com.example.myframework2.mvc.core.di.factory.example.MyUserService;
import com.example.myframework2.mvc.core.di.factory.example.QnaController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BeanFactoryTest {
    private Logger log = LoggerFactory.getLogger(BeanFactoryTest.class);

    private BeanFactory beanFactory;

    @BeforeEach
    void setup() {
        BeanScanner scanner = new BeanScanner("com.example.myframework2.mvc.core.di.factory.example");
        Set<Class<?>> preInstanticateClazz = scanner.scan();
        beanFactory = new BeanFactory(preInstanticateClazz);
        beanFactory.initialize();
    }

    @Test
    void constructorDi() throws Exception {
        QnaController qnaController = beanFactory.getBean(QnaController.class);

        assertNotNull(qnaController);
        assertNotNull(qnaController.getQnaService());

        MyQnaService qnaService = qnaController.getQnaService();
        assertNotNull(qnaService.getUserRepository());
        assertNotNull(qnaService.getQuestionRepository());

    }

    @Test
    void fieldDi() {
        MyUserService userService = beanFactory.getBean(MyUserService.class);
        assertNotNull(userService);
        assertNotNull(userService.getUserRepository());
    }

    @Test
    void setterDi() {
        MyUserController userController = beanFactory.getBean(MyUserController.class);

        assertNotNull(userController);
        assertNotNull(userController.getMyUserService());
    }

    @Test
    void getConstorllers() {
        Map<Class<?>, Object> controllers = beanFactory.getControllers();
        Set<Class<?>> keys = controllers.keySet();
        for (Class<?> clazz : keys) {
            log.debug("Bean : {}", clazz);
        }
    }

    @AfterEach
    void tearDown() {
        beanFactory.clear();
    }
}
