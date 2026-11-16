package com.example.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RandomKeyGeneratorTest {

    @Test
    void generatesKeyOfConfiguredLength() {
        assertThat(new RandomKeyGenerator(new SecureRandom(), 7).generate()).hasSize(7);
        assertThat(new RandomKeyGenerator(new SecureRandom(), 2).generate()).hasSize(2);
    }

    @Test
    void generatesOnlyBase62Chars() {
        RandomKeyGenerator g = new RandomKeyGenerator(new SecureRandom(), 7);
        for (int i = 0; i < 1000; i++) {
            assertThat(g.generate()).matches("^[0-9a-zA-Z]{7}$");
        }
    }

    // 난수원을 실제로 사용하는지 확인
    @Test
    void sameSeedGivesSameKey() {
        assertThat(new RandomKeyGenerator(new Random(42), 7).generate())
                .isEqualTo(new RandomKeyGenerator(new Random(42), 7).generate());
    }

    @Test
    void rejectsNonPositiveLength() {
        assertThatThrownBy(() -> new RandomKeyGenerator(new SecureRandom(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // short_key 컬럼이 VARCHAR(16) 이므로 17 이상은 거부하고 16 은 허용한다
    @Test
    void rejectsLengthOver16() {
        assertThatThrownBy(() -> new RandomKeyGenerator(new SecureRandom(), 17))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1~16");
        assertThat(new RandomKeyGenerator(new SecureRandom(), 16).generate()).hasSize(16);
    }
}
