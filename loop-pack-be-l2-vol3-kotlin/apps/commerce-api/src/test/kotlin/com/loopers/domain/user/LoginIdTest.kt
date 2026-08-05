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

class LoginIdTest {
    @DisplayName("로그인 ID 를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("영문 및 숫자 10자 이내면, 정상 생성된다.")
        @ParameterizedTest
        @ValueSource(strings = ["a", "loopers01", "ABCDEFGHIJ", "1234567890"])
        fun createsLoginId_whenValueIsValid(value: String) {
            // act
            val loginId = LoginId(value)

            // assert
            assertThat(loginId.value).isEqualTo(value)
        }

        @DisplayName("'영문 및 숫자 10자 이내' 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", "loopers_01", "loopers 01", "루퍼스01", "abcdefghijk"])
        fun throwsBadRequestException_whenValueIsInvalid(value: String) {
            // act
            val result = assertThrows<CoreException> { LoginId(value) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("로그인 ID 는 값 객체이므로, ")
    @Nested
    inner class ValueSemantics {
        @DisplayName("같은 값이면 동등하고, toString 은 값을 그대로 반환한다.")
        @Test
        fun equalsByValue_andExposesRawValueInToString() {
            // arrange
            val first = LoginId("loopers01")
            val second = LoginId("loopers01")

            // assert
            assertAll(
                { assertThat(first).isEqualTo(second) },
                { assertThat(first.hashCode()).isEqualTo(second.hashCode()) },
                { assertThat(first.toString()).isEqualTo("loopers01") },
            )
        }
    }
}
