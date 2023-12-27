package com.example.myframework2.mvc.di.examples;

import com.example.myframework2.mvc.core.annotation.Inject;
import com.example.myframework2.mvc.core.annotation.Repository;

import javax.sql.DataSource;

@Repository
public class JdbcUserRepository implements UserRepository {

    @Inject
    private DataSource dataSource;

    public DataSource getDataSource() {
        return dataSource;
    }
}
