package com.loopers.config

import com.loopers.infrastructure.payment.PgClient
import com.loopers.infrastructure.payment.PgProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.Configuration

/** 스캔 범위를 PgClient 패키지로 좁힌다. 앱 전체를 스캔하면 Feign 클라이언트가 어디서 생기는지 추적하기 어려워진다. */
@Configuration
@EnableFeignClients(basePackageClasses = [PgClient::class])
@EnableConfigurationProperties(PgProperties::class)
class PgClientConfig
