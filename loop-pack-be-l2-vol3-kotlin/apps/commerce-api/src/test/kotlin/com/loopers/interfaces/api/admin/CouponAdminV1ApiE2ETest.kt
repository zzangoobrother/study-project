package com.loopers.interfaces.api.admin

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponName
import com.loopers.domain.coupon.DiscountType
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import com.loopers.interfaces.api.admin.coupon.CouponAdminV1Dto
import com.loopers.support.auth.AdminAuthInterceptor
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.time.ZonedDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponAdminV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val couponJpaRepository: CouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT = "/api-admin/v1/coupons"
        private const val ADMIN_ID = "admin"
        private const val ADMIN_PW = "admin1234"
    }

    private val couponType = object : ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>>() {}
    private val pageType =
        object : ParameterizedTypeReference<ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>>>() {}

    private fun adminHeaders(): HttpHeaders = HttpHeaders().apply {
        set(AdminAuthInterceptor.HEADER_LDAP_ID, ADMIN_ID)
        set(AdminAuthInterceptor.HEADER_LDAP_PW, ADMIN_PW)
        contentType = MediaType.APPLICATION_JSON
    }

    private fun savedCoupon(
        name: String = "신규가입 10% 할인",
        discountType: DiscountType = DiscountType.RATE,
        discountValue: Long = 10,
        minOrderAmount: Long = 10_000,
    ): CouponModel = couponJpaRepository.save(
        CouponModel.create(
            name = CouponName(name),
            discountType = discountType,
            discountValue = discountValue,
            minOrderAmount = minOrderAmount,
            expiresAt = ZonedDateTime.now().plusDays(30),
        ),
    )

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api-admin/v1/coupons")
    @Nested
    inner class Register {
        @DisplayName("정률 정책을 등록하면 명세의 필드명으로 응답한다.")
        @Test
        fun registersRateCoupon() {
            // arrange
            val request = CouponAdminV1Dto.RegisterRequest(
                name = "신규가입 10% 할인",
                type = DiscountType.RATE,
                value = 10,
                minOrderAmount = 10_000,
                expiredAt = ZonedDateTime.now().plusDays(30),
            )

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(request, adminHeaders()),
                couponType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.id).isNotNull() },
                { assertThat(response.body?.data?.type).isEqualTo(DiscountType.RATE) },
                { assertThat(response.body?.data?.value).isEqualTo(10L) },
                { assertThat(response.body?.data?.minOrderAmount).isEqualTo(10_000L) },
                { assertThat(response.body?.data?.issuedCount).isEqualTo(0L) },
                { assertThat(response.body?.data?.deleted).isFalse() },
            )
        }

        @DisplayName("정률 값이 100 을 넘으면 400 이다.")
        @Test
        fun returnsBadRequest_whenRateExceedsHundred() {
            // arrange
            val request = CouponAdminV1Dto.RegisterRequest(
                name = "이상한 쿠폰",
                type = DiscountType.RATE,
                value = 101,
                expiredAt = ZonedDateTime.now().plusDays(30),
            )

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(request, adminHeaders()),
                couponType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("인증 헤더가 없으면 401 이다. 인터셉터가 경로로 막는다.")
        @Test
        fun returnsUnauthorized_whenHeadersMissing() {
            // arrange
            val request = CouponAdminV1Dto.RegisterRequest(
                name = "신규가입",
                type = DiscountType.FIXED,
                value = 5_000,
                expiredAt = ZonedDateTime.now().plusDays(30),
            )
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(request, headers),
                couponType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}")
    @Nested
    inner class GetCoupon {
        @DisplayName("삭제된 정책도 200 이며 deleted 가 true 다.")
        @Test
        fun returnsDeletedCoupon() {
            // arrange
            val coupon = savedCoupon()
            testRestTemplate.exchange(
                "$ENDPOINT/${coupon.id}",
                HttpMethod.DELETE,
                HttpEntity<Any>(adminHeaders()),
                couponType,
            )

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${coupon.id}",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                couponType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.deleted).isTrue() },
                { assertThat(response.body?.data?.deletedAt).isNotNull() },
            )
        }

        @DisplayName("없는 정책이면 404 다.")
        @Test
        fun returnsNotFound_whenMissing() {
            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/999999",
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                couponType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    @DisplayName("PUT /api-admin/v1/coupons/{couponId}")
    @Nested
    inner class Change {
        @DisplayName("전 필드가 교체된다.")
        @Test
        fun replacesAllFields() {
            // arrange
            val coupon = savedCoupon()
            val request = CouponAdminV1Dto.ChangeRequest(
                name = "가을맞이 3천원",
                type = DiscountType.FIXED,
                value = 3_000,
                minOrderAmount = 20_000,
                expiredAt = ZonedDateTime.now().plusDays(60),
            )

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${coupon.id}",
                HttpMethod.PUT,
                HttpEntity(request, adminHeaders()),
                couponType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.name).isEqualTo("가을맞이 3천원") },
                { assertThat(response.body?.data?.type).isEqualTo(DiscountType.FIXED) },
                { assertThat(response.body?.data?.value).isEqualTo(3_000L) },
                { assertThat(response.body?.data?.minOrderAmount).isEqualTo(20_000L) },
            )
        }

        @DisplayName("삭제된 정책을 수정하면 409 다.")
        @Test
        fun returnsConflict_whenDeleted() {
            // arrange
            val coupon = savedCoupon()
            testRestTemplate.exchange(
                "$ENDPOINT/${coupon.id}",
                HttpMethod.DELETE,
                HttpEntity<Any>(adminHeaders()),
                couponType,
            )
            val request = CouponAdminV1Dto.ChangeRequest(
                name = "새 이름",
                type = DiscountType.FIXED,
                value = 1_000,
                expiredAt = ZonedDateTime.now().plusDays(60),
            )

            // act
            val response = testRestTemplate.exchange(
                "$ENDPOINT/${coupon.id}",
                HttpMethod.PUT,
                HttpEntity(request, adminHeaders()),
                couponType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }
    }

    @DisplayName("DELETE /api-admin/v1/coupons/{couponId}")
    @Nested
    inner class Delete {
        @DisplayName("두 번 삭제해도 200 이다. 멱등하다.")
        @Test
        fun isIdempotent() {
            // arrange
            val coupon = savedCoupon()
            val deleteType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

            // act
            val first = testRestTemplate.exchange(
                "$ENDPOINT/${coupon.id}",
                HttpMethod.DELETE,
                HttpEntity<Any>(adminHeaders()),
                deleteType,
            )
            val second = testRestTemplate.exchange(
                "$ENDPOINT/${coupon.id}",
                HttpMethod.DELETE,
                HttpEntity<Any>(adminHeaders()),
                deleteType,
            )

            // assert
            assertAll(
                { assertThat(first.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(second.statusCode).isEqualTo(HttpStatus.OK) },
            )
        }
    }

    @DisplayName("GET /api-admin/v1/coupons")
    @Nested
    inner class GetCoupons {
        @DisplayName("최신순으로 반환하며 발급 건수를 포함한다.")
        @Test
        fun returnsCouponsWithIssuedCount() {
            // arrange
            savedCoupon(name = "먼저 만든 정책")
            savedCoupon(name = "나중에 만든 정책")

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.GET,
                HttpEntity<Any>(adminHeaders()),
                pageType,
            )

            // assert
            val content = response.body?.data?.content
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.totalElements).isEqualTo(2L) },
                // 최신순이므로 나중에 만든 것이 앞이다
                { assertThat(content?.first()?.name).isEqualTo("나중에 만든 정책") },
                { assertThat(content?.first()?.issuedCount).isEqualTo(0L) },
            )
        }
    }
}
