package com.example.urlshortener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
abstract class IntegrationTestSupport {

    // 모든 하위 테스트 클래스가 공유하는 MySQL 컨테이너
    @Container
    @ServiceConnection
    static MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    protected MockMvc mockMvc;
}
