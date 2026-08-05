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

class EmailTest {
    @DisplayName("이메일을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("'xx@yy.zz' 형식이면, 정상 생성된다.")
        @ParameterizedTest
        @ValueSource(strings = ["loopers@loopers.com", "a.b+c_d-e%f@sub.domain.co.kr"])
        fun createsEmail_whenValueIsValid(value: String) {
            // act
            val email = Email(value)

            // assert
            assertThat(email.value).isEqualTo(value)
        }

        @DisplayName("'xx@yy.zz' 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", "loopers", "loopers@", "@loopers.com", "loopers@loopers", "loopers@loopers."])
        fun throwsBadRequestException_whenValueIsInvalid(value: String) {
            // act
            val result = assertThrows<CoreException> { Email(value) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("형식은 유효하지만 254자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenValueExceedsMaxLength() {
            // arrange
            val overLengthEmail = "a".repeat(250) + "@b.com"

            // act
            val result = assertThrows<CoreException> { Email(overLengthEmail) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("정확히 254자면, 정상 생성된다.")
        @Test
        fun createsEmail_whenValueIsExactlyMaxLength() {
            // arrange
            val maxLengthEmail = "a".repeat(248) + "@b.com"

            // act
            val email = Email(maxLengthEmail)

            // assert
            assertThat(email.value).hasSize(254)
        }
    }

    @DisplayName("이메일은 값 객체이므로, ")
    @Nested
    inner class ValueSemantics {
        @DisplayName("같은 값이면 동등하고, toString 은 값을 그대로 반환한다.")
        @Test
        fun equalsByValue_andExposesRawValueInToString() {
            // arrange
            val first = Email("loopers@loopers.com")
            val second = Email("loopers@loopers.com")

            // assert
            assertAll(
                { assertThat(first).isEqualTo(second) },
                { assertThat(first.hashCode()).isEqualTo(second.hashCode()) },
                { assertThat(first.toString()).isEqualTo("loopers@loopers.com") },
            )
        }
    }
}
