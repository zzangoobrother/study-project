package com.example.myframework2.mvc.di.examples;

import javax.sql.DataSource;

public class MyJdbcTemplate {
    private DataSource dataSource;

    public MyJdbcTemplate(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public DataSource getDataSource() {
        return dataSource;
    }
}
