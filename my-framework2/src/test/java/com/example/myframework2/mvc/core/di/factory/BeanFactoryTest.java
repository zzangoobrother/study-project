package com.example.myframework2.mvc.core.di.factory;

import com.example.myframework2.mvc.core.di.context.annotation.ClasspathBeanDefinitionScanner;
import com.example.myframework2.mvc.core.di.factory.example.MyQnaService;
import com.example.myframework2.mvc.core.di.factory.example.MyUserController;
import com.example.myframework2.mvc.core.di.factory.example.MyUserService;
import com.example.myframework2.mvc.core.di.factory.example.QnaController;
import com.example.myframework2.mvc.core.di.beans.factory.support.DefaultBeanFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BeanFactoryTest {
    private Logger log = LoggerFactory.getLogger(BeanFactoryTest.class);

    private DefaultBeanFactory beanFactory;

    @BeforeEach
    void setup() {
        beanFactory = new DefaultBeanFactory();
        ClasspathBeanDefinitionScanner scanner = new ClasspathBeanDefinitionScanner(beanFactory);
        scanner.doScan("com.example.myframework2.mvc.core.di.factory.example");
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

    @AfterEach
    void tearDown() {
        beanFactory.clear();
    }
}
