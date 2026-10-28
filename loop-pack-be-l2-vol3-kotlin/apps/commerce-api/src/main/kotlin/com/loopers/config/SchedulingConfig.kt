package com.loopers.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * pg.sync.enabled 가 true 일 때만 스케줄링을 켠다. 테스트 프로필은 끈다 — 테스트 도중 동기화가 끼어들면 결과가 흔들린다.
 * (2026-10-13 결제 설계 5.3 장)
 *
 * 주문 만료 스케줄러(OrderExpirationScheduler)도 같은 스위치를 따른다. 속성 이름이 결제에 묶여 어색하지만 바꾸지 않았다 —
 * 바꾸면 측정 구성(docker/loadtest-payment.override.yml 등)까지 손대야 한다. 후속 정리 후보다. (2026-10-28 주문 만료 설계 5 장)
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "pg.sync", name = ["enabled"], havingValue = "true")
class SchedulingConfig
