package com.example.myframework2.mvc.di.examples;

import com.example.myframework2.mvc.core.annotation.Bean;
import com.example.myframework2.mvc.core.annotation.Configuration;
import org.apache.commons.dbcp2.BasicDataSource;

import javax.sql.DataSource;

@Configuration
public class ExampleConfig {

    @Bean
    public DataSource dataSource() {
        BasicDataSource basicDataSource = new BasicDataSource();
        basicDataSource.setDriverClassName("org.h2.Driver");
        basicDataSource.setUrl("jdbc:h2:tcp://localhost/~/study;MODE=MYSQL");
        basicDataSource.setUsername("sa");
        basicDataSource.setPassword("123");

        return basicDataSource;
    }
}
