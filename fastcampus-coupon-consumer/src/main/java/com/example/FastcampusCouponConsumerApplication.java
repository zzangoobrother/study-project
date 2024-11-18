package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Import(CouponCoreConfiguration.class)
@SpringBootApplication
public class FastcampusCouponConsumerApplication {

    public static void main(String[] args) {
        System.setProperty("spring.config.name", "application-core,application-consumer");
        SpringApplication.run(FastcampusCouponConsumerApplication.class, args);
    }
}
