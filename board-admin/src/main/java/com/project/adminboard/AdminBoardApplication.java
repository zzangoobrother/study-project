package com.project.adminboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class AdminBoardApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminBoardApplication.class, args);
    }
}
