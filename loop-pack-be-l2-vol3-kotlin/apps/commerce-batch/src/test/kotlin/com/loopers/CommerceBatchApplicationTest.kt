package com.loopers

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

/**
 * application.yml 의 spring.batch.job.name 기본값은 NONE 이고 spring.batch.job.enabled 기본값은 true 다.
 * 잡 이름 없이 컨텍스트를 띄우면 JobLauncherApplicationRunner 가 없는 잡 'NONE' 을 찾다가 컨텍스트 로드가 실패한다.
 * 이 테스트는 컨텍스트 로드만 보므로 러너를 끈다.
 */
@SpringBootTest
@TestPropertySource(properties = ["spring.batch.job.enabled=false"])
class CommerceBatchApplicationTest {
    @Test
    fun contextLoads() {}
}
