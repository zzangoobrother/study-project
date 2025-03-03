package com.example.dbconnectiontest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class DbConnectionTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(DbConnectionTestApplication.class, args);
    }
}
