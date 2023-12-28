package com.example.myframework2.mvc.core.di.factory;

import com.example.myframework2.mvc.core.di.beans.factory.support.BeanDefinitionReader;
import com.example.myframework2.mvc.core.di.context.annotation.AnnotatedBeanDefinitionReader;
import com.example.myframework2.mvc.core.di.context.annotation.ClasspathBeanDefinitionScanner;
import com.example.myframework2.mvc.core.di.beans.factory.support.DefaultBeanFactory;
import com.example.myframework2.mvc.di.examples.ExampleConfig;
import com.example.myframework2.mvc.di.examples.IntegrationConfig;
import com.example.myframework2.mvc.di.examples.JdbcUserRepository;
import com.example.myframework2.mvc.di.examples.MyJdbcTemplate;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AnnotatedBeanDefinitionReaderTest {
    @Test
    void register_simple() {
        DefaultBeanFactory beanFactory = new DefaultBeanFactory();
        BeanDefinitionReader abdr = new AnnotatedBeanDefinitionReader(beanFactory);
        abdr.loadBeanDefinitions(ExampleConfig.class);
        beanFactory.initialize();

        assertNotNull(beanFactory.getBean(DataSource.class));
    }

    @Test
    void register_ClasspathBeanDefinitionScanner_통합() {
        DefaultBeanFactory beanFactory = new DefaultBeanFactory();
        BeanDefinitionReader abdr = new AnnotatedBeanDefinitionReader(beanFactory);
        abdr.loadBeanDefinitions(IntegrationConfig.class);

        ClasspathBeanDefinitionScanner cbds = new ClasspathBeanDefinitionScanner(beanFactory);
        cbds.doScan("com.example.myframework2.mvc.di.examples");

        beanFactory.initialize();

        assertNotNull(beanFactory.getBean(DataSource.class));

        JdbcUserRepository userRepository = beanFactory.getBean(JdbcUserRepository.class);
        assertNotNull(userRepository);
        assertNotNull(userRepository.getDataSource());

        MyJdbcTemplate jdbcTemplate = beanFactory.getBean(MyJdbcTemplate.class);
        assertNotNull(jdbcTemplate);
        assertNotNull(jdbcTemplate.getDataSource());
    }
}
