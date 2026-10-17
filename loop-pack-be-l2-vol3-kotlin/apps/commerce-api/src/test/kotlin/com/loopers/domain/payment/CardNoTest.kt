package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CardNoTest {
    @DisplayName("xxxx-xxxx-xxxx-xxxx 형식이면 만들어진다.")
    @Test
    fun createsCardNo_whenFormatIsValid() {
        // act
        val cardNo = CardNo("1234-5678-9814-1451")

        // assert
        assertThat(cardNo.value).isEqualTo("1234-5678-9814-1451")
    }

    @DisplayName("형식이 다르면 400 BAD_REQUEST 예외가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = ["", "1234567898141451", "1234-5678-9814-145", "1234-5678-9814-14511", "abcd-5678-9814-1451", "1234 5678 9814 1451"])
    fun throwsBadRequest_whenFormatIsInvalid(value: String) {
        // act
        val result = assertThrows<CoreException> { CardNo(value) }

        // assert
        assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    @DisplayName("문자열로 바꾸면 마지막 네 자리만 남기고 가린다.")
    @Test
    fun masksExceptLastFourDigits_whenConvertedToString() {
        // act
        val text = CardNo("1234-5678-9814-1451").toString()

        // assert
        assertThat(text).isEqualTo("****-****-****-1451")
    }
}
