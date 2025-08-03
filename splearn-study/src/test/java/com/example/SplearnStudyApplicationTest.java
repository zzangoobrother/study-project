package com.example;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;

class SplearnStudyApplicationTest {

    @Test
    void run() {
        try (MockedStatic<SpringApplication> mock = Mockito.mockStatic(SpringApplication.class)) {
            SplearnStudyApplication.main(new String[0]);

            mock.verify(() -> SpringApplication.run(SplearnStudyApplication.class, new String[0]));
        }
    }
}
