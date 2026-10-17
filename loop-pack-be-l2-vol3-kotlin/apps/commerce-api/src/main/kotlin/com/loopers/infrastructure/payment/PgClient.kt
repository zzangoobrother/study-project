package com.loopers.infrastructure.payment

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam

/**
 * PG HTTP 선언. 이 인터페이스를 직접 부르지 않는다 — 재시도 · 서킷은 PgResilientClient 가 감싼다.
 * 타임아웃은 spring.cloud.openfeign.client.config.pg-simulator 에 있다. name 을 바꾸면 그 설정이 조용히 빠진다.
 */
@FeignClient(name = "pg-simulator", url = "\${pg.base-url}")
interface PgClient {
    @PostMapping("/api/v1/payments")
    fun requestPayment(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: PgDto.PaymentRequest,
    ): PgDto.Response<PgDto.TransactionResponse>

    @GetMapping("/api/v1/payments/{transactionKey}")
    fun getTransaction(
        @RequestHeader("X-USER-ID") userId: String,
        @PathVariable("transactionKey") transactionKey: String,
    ): PgDto.Response<PgDto.TransactionDetailResponse>

    @GetMapping("/api/v1/payments")
    fun getTransactionsByOrder(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestParam("orderId") orderId: String,
    ): PgDto.Response<PgDto.OrderResponse>
}
