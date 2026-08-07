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
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class UserNameTest {
    @DisplayName("이름을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("한글 또는 영문 20자 이내면, 정상 생성된다.")
        @ParameterizedTest
        @ValueSource(strings = ["홍길동", "HongGilDong", "가나다라마바사아자차카타파하가나다라마바"])
        fun createsUserName_whenValueIsValid(value: String) {
            // act
            val name = UserName(value)

            // assert
            assertThat(name.value).isEqualTo(value)
        }

        @DisplayName("'한글 또는 영문 20자 이내' 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", "홍 길동", "홍길동2", "홍길동!", "가나다라마바사아자차카타파하가나다라마바사"])
        fun throwsBadRequestException_whenValueIsInvalid(value: String) {
            // act
            val result = assertThrows<CoreException> { UserName(value) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("이름은 값 객체이므로, ")
    @Nested
    inner class ValueSemantics {
        @DisplayName("같은 값이면 동등하고, toString 은 값을 그대로 반환한다.")
        @Test
        fun equalsByValue_andExposesRawValueInToString() {
            // arrange
            val first = UserName("홍길동")
            val second = UserName("홍길동")

            // assert
            assertAll(
                { assertThat(first).isEqualTo(second) },
                { assertThat(first.hashCode()).isEqualTo(second.hashCode()) },
                { assertThat(first.toString()).isEqualTo("홍길동") },
            )
        }
    }

    @DisplayName("이름을 마스킹할 때, ")
    @Nested
    inner class Masking {
        @DisplayName("마지막 글자가 마스킹 문자로 가려진다.")
        @ParameterizedTest
        @CsvSource(
            "홍길동, 홍길*",
            "HongGilDong, HongGilDon*",
            "가나다라마바사아자차카타파하가나다라마바, 가나다라마바사아자차카타파하가나다라마*",
        )
        fun masksLastCharacter(value: String, expected: String) {
            // arrange
            val name = UserName(value)

            // act
            val result = name.masked()

            // assert
            assertThat(result).isEqualTo(expected)
        }

        @DisplayName("1글자 이름은 전체가 가려진다.")
        @Test
        fun masksEntireName_whenNameIsSingleCharacter() {
            // arrange
            val name = UserName("김")

            // act
            val result = name.masked()

            // assert
            assertThat(result).isEqualTo("*")
        }
    }
}
