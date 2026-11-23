package com.example.urlshortener;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base62Test {

    @Test
    void encodesZero() {
        assertThat(Base62.encode(0)).isEqualTo("0");
    }

    @Test
    void encodesSingleDigits() {
        assertThat(Base62.encode(10)).isEqualTo("a");
        assertThat(Base62.encode(36)).isEqualTo("A");
        assertThat(Base62.encode(61)).isEqualTo("Z");
    }

    @Test
    void encodesCarry() {
        assertThat(Base62.encode(62)).isEqualTo("10");
        assertThat(Base62.encode(3843)).isEqualTo("ZZ");
    }

    @Test
    void roundTrip() {
        for (long v : new long[]{0, 1, 61, 62, 3844, 123_456_789L, Long.MAX_VALUE}) {
            assertThat(Base62.decode(Base62.encode(v))).isEqualTo(v);
        }
    }

    @Test
    void rejectsNegative() {
        assertThatThrownBy(() -> Base62.encode(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidChar() {
        assertThatThrownBy(() -> Base62.decode("ab-c")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmpty() {
        assertThatThrownBy(() -> Base62.decode("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encodePaddedFillsLeftWithZero() {
        assertThat(Base62.encodePadded(0, 7)).isEqualTo("0000000");
        assertThat(Base62.encodePadded(FeistelPermutation.SHORT_KEY_SPACE - 1, 7)).isEqualTo("ZZZZZZZ");
        assertThat(Base62.decode(Base62.encodePadded(62, 7))).isEqualTo(62);
    }

    @Test
    void encodePaddedRejectsOverflow() {
        assertThatThrownBy(() -> Base62.encodePadded(FeistelPermutation.SHORT_KEY_SPACE, 7))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
