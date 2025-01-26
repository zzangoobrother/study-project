package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class FcmProjectApplication {
    public static void main(String[] args) {
        SpringApplication.run(FcmProjectApplication.class, args);
    }
}
