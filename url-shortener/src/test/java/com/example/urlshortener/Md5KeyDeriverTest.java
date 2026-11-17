package com.example.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class Md5KeyDeriverTest {

    @Test
    void fixedVectors() {
        Md5KeyDeriver d7 = new Md5KeyDeriver(7);
        assertThat(d7.keyOf("https://example.com", 0)).isEqualTo("CeN9SYX");
        assertThat(d7.keyOf("https://example.com", 1)).isEqualTo("c8s9jo0");      // salt "#1"
        assertThat(d7.keyOf("https://example.com/한글", 0)).isEqualTo("tOv9Nq3");   // UTF-8
        assertThat(new Md5KeyDeriver(3).keyOf("https://example.com", 0)).isEqualTo("SYX");
    }

    @Test
    void padsWithLeadingZero() {
        assertThat(new Md5KeyDeriver(3).keyOf("https://example.com/p160", 0)).isEqualTo("0pP");
    }

    @Test
    void sameInputSameKeyAndBase62Only() {
        Md5KeyDeriver d = new Md5KeyDeriver(7);
        assertThat(d.keyOf("https://a.com/x", 0)).isEqualTo(d.keyOf("https://a.com/x", 0)).matches("^[0-9a-zA-Z]{7}$");
    }

    @Test
    void maxLengthWorksBeyondLongRange() {
        // 62^16 은 long 범위를 넘으므로 BigInteger 경로를 검증한다
        for (int n = 0; n < 50; n++) {
            assertThat(new Md5KeyDeriver(16).keyOf("https://example.com/" + n, 0)).matches("^[0-9a-zA-Z]{16}$");
        }
        // 짧은 키는 긴 키의 접미사이다 (mod 62^L 의 하위 자릿수)
        String k16 = new Md5KeyDeriver(16).keyOf("https://example.com", 0);
        assertThat(k16).endsWith("CeN9SYX");
    }

    @Test
    void rejectsInvalidArguments() {
        assertThatThrownBy(() -> new Md5KeyDeriver(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Md5KeyDeriver(17)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Md5KeyDeriver(7).keyOf("https://a.com", -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
