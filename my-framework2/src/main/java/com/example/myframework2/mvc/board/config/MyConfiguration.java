package com.example.myframework2.mvc.board.config;

import com.example.myframework2.mvc.core.annotation.Bean;
import com.example.myframework2.mvc.core.annotation.Configuration;
import com.example.myframework2.mvc.core.jdbc.JdbcTemplate;
import org.apache.commons.dbcp2.BasicDataSource;
import org.springframework.context.annotation.ComponentScan;

import javax.sql.DataSource;

@Configuration
@ComponentScan({ "com.example.myframework2.mvc" })
public class MyConfiguration {
    @Bean
    public DataSource dataSource() {
        BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:tcp://localhost/~/study;MODE=MYSQL");
        ds.setUsername("sa");
        ds.setPassword("123");
        return ds;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
