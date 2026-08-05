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

class RawPasswordTest {
    @DisplayName("평문 비밀번호를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("8~16자이며 영문·숫자·특수문자를 각각 포함하면, 정상 생성된다.")
        @ParameterizedTest
        @ValueSource(strings = ["Loopers1!", "abcdefg1!", "Abcdefghij12345!"])
        fun createsRawPassword_whenValueIsValid(value: String) {
            // act
            val rawPassword = RawPassword(value)

            // assert — value 는 internal 이지만 test 소스셋에서는 보인다.
            assertThat(rawPassword.value).isEqualTo(value)
        }

        @DisplayName("8~16자 범위를 벗어나면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["Ab1!", "Abc123!", "Abcdefghij12345!@"])
        fun throwsBadRequestException_whenLengthIsOutOfRange(value: String) {
            // act
            val result = assertThrows<CoreException> { RawPassword(value) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("영문·숫자·특수문자 중 하나라도 빠지면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["abcdefgh", "Password1", "Abcdefg!", "12345678!", "!@#\$%^&*"])
        fun throwsBadRequestException_whenAnyCharacterTypeIsMissing(value: String) {
            // act
            val result = assertThrows<CoreException> { RawPassword(value) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("허용되지 않은 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["비밀번호1234!", "Pass word1!", "Loopers1!\t"])
        fun throwsBadRequestException_whenDisallowedCharacterIsIncluded(value: String) {
            // act
            val result = assertThrows<CoreException> { RawPassword(value) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("평문 비밀번호의 부분 일치를 판정할 때, ")
    @Nested
    inner class Contains {
        @DisplayName("포함된 문자열이면 true, 아니면 false 를 반환한다.")
        @Test
        fun returnsWhetherTextIsIncluded() {
            // arrange
            val rawPassword = RawPassword("Abc19900101!")

            // assert
            assertAll(
                { assertThat(rawPassword.contains("19900101")).isTrue() },
                { assertThat(rawPassword.contains("900101")).isTrue() },
                { assertThat(rawPassword.contains("20000101")).isFalse() },
            )
        }
    }

    @DisplayName("평문 비밀번호는 민감값이므로, ")
    @Nested
    inner class Masking {
        @DisplayName("toString 에 평문이 노출되지 않는다.")
        @Test
        fun doesNotExposeRawValueInToString() {
            // arrange
            val rawPassword = RawPassword("Loopers1!")

            // act
            val result = rawPassword.toString()

            // assert
            assertAll(
                { assertThat(result).doesNotContain("Loopers1!") },
                { assertThat(result).isEqualTo("****") },
            )
        }

        @DisplayName("평문 비밀번호를 담은 data class 의 자동 toString 에도 평문이 노출되지 않는다.")
        @Test
        fun doesNotExposeRawValue_whenNestedInDataClass() {
            // arrange
            data class Holder(val password: RawPassword)

            // act
            val result = Holder(RawPassword("Loopers1!")).toString()

            // assert
            assertThat(result).doesNotContain("Loopers1!")
        }
    }

    @DisplayName("평문 비밀번호는 값 객체이므로, ")
    @Nested
    inner class ValueSemantics {
        @DisplayName("같은 값이면 동등하고, 다른 값이면 동등하지 않다.")
        @Test
        fun equalsByValue() {
            // arrange
            val first = RawPassword("Loopers1!")
            val second = RawPassword("Loopers1!")
            val other = RawPassword("Loopers2@")

            // assert
            assertAll(
                { assertThat(first).isEqualTo(second) },
                { assertThat(first.hashCode()).isEqualTo(second.hashCode()) },
                { assertThat(first).isNotEqualTo(other) },
            )
        }
    }
}
