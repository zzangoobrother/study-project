package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate

class BirthDateTest {
    @DisplayName("문자열로 생년월일을 생성할 때, ")
    @Nested
    inner class From {
        @DisplayName("yyyy-MM-dd 형식의 과거 날짜면, 정상 생성된다.")
        @Test
        fun createsBirthDate_whenTextIsValid() {
            // act
            val birthDate = BirthDate.from("1990-01-01")

            // assert
            assertThat(birthDate.value).isEqualTo(LocalDate.of(1990, 1, 1))
        }

        @DisplayName("yyyy-MM-dd 형식이 아니거나 실재하지 않는 날짜면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", "1990/01/01", "19900101", "1990-1-1", "1990-13-01", "1990-02-30"])
        fun throwsBadRequestException_whenTextIsInvalid(text: String) {
            // act
            val result = assertThrows<CoreException> { BirthDate.from(text) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("오늘이면, 정상 생성된다.")
        @Test
        fun createsBirthDate_whenTextIsToday() {
            // arrange
            val today = LocalDate.now()

            // act
            val birthDate = BirthDate.from(today.toString())

            // assert
            assertThat(birthDate.value).isEqualTo(today)
        }

        @DisplayName("미래면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenTextIsInFuture() {
            // arrange
            val tomorrow = LocalDate.now().plusDays(1).toString()

            // act
            val result = assertThrows<CoreException> { BirthDate.from(tomorrow) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("LocalDate 로 생년월일을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("과거 날짜면, 정상 생성된다.")
        @Test
        fun createsBirthDate_whenValueIsInPast() {
            // arrange
            val value = LocalDate.of(1990, 1, 1)

            // act
            val birthDate = BirthDate(value)

            // assert
            assertThat(birthDate.value).isEqualTo(value)
        }

        @DisplayName("미래면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenValueIsInFuture() {
            // arrange
            val tomorrow = LocalDate.now().plusDays(1)

            // act
            val result = assertThrows<CoreException> { BirthDate(tomorrow) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("생년월일은 값 객체이므로, ")
    @Nested
    inner class ValueSemantics {
        @DisplayName("같은 값이면 동등하고, toString 은 ISO 표기를 반환한다.")
        @Test
        fun equalsByValue_andExposesIsoTextInToString() {
            // arrange
            val first = BirthDate.from("1990-01-01")
            val second = BirthDate(LocalDate.of(1990, 1, 1))

            // assert
            assertAll(
                { assertThat(first).isEqualTo(second) },
                { assertThat(first.hashCode()).isEqualTo(second.hashCode()) },
                { assertThat(first.toString()).isEqualTo("1990-01-01") },
            )
        }
    }
}
