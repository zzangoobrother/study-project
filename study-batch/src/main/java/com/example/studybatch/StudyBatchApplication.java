package com.example.studybatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class StudyBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudyBatchApplication.class, args);
    }

}
