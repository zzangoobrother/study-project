package com.loopers.domain.brand

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class BrandModelTest {
    private fun brand(): BrandModel =
        BrandModel.create(BrandName("루퍼스"), BrandDescription("일상을 조금 낫게"))

    @DisplayName("브랜드 정보를 변경할 때, ")
    @Nested
    inner class Change {
        @DisplayName("이름과 설명이 모두 교체된다.")
        @Test
        fun replacesNameAndDescription() {
            // arrange
            val sut = brand()

            // act
            sut.change(BrandName("몬드리안"), BrandDescription("선과 면"))

            // assert
            assertAll(
                { assertThat(sut.name).isEqualTo(BrandName("몬드리안")) },
                { assertThat(sut.description).isEqualTo(BrandDescription("선과 면")) },
            )
        }

        /**
         * PUT 은 전체 교체다. 설명을 생략한 요청은 DTO 가 BrandDescription.EMPTY 로 변환해 넘기고,
         * 애그리거트는 그것을 그대로 덮어쓴다. "생략했으니 유지" 는 PATCH 의 의미이며 이 API 의 계약이 아니다.
         */
        @DisplayName("빈 설명으로 변경하면, 기존 설명이 유지되지 않고 빈 값으로 덮인다.")
        @Test
        fun overwritesDescriptionWithEmpty() {
            // arrange
            val sut = brand()

            // act
            sut.change(BrandName("루퍼스"), BrandDescription.EMPTY)

            // assert
            assertThat(sut.description).isEqualTo(BrandDescription.EMPTY)
        }
    }
}
