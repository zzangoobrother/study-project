package com.example.myframework2.mvc.core.di.factory;

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
        BeanFactory beanFactory = new BeanFactory();
        AnnotatedBeanDefinitionReader abdr = new AnnotatedBeanDefinitionReader(beanFactory);
        abdr.register(ExampleConfig.class);
        beanFactory.initialize();

        assertNotNull(beanFactory.getBean(DataSource.class));
    }

    @Test
    void register_ClasspathBeanDefinitionScanner_통합() {
        BeanFactory beanFactory = new BeanFactory();
        AnnotatedBeanDefinitionReader abdr = new AnnotatedBeanDefinitionReader(beanFactory);
        abdr.register(IntegrationConfig.class);

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
