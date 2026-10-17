package com.loopers.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * pg.sync.enabled 가 true 일 때만 스케줄링을 켠다. 테스트 프로필은 끈다 — 테스트 도중 동기화가 끼어들면 결과가 흔들린다.
 * (2026-10-13 결제 설계 5.3 장)
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "pg.sync", name = ["enabled"], havingValue = "true")
class SchedulingConfig
