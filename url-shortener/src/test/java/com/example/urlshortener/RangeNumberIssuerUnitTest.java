package com.example.urlshortener;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RangeNumberIssuerUnitTest {

    @Test
    void retriesAllocationAfterFailure() {
        KeyRangeAllocator allocator = mock(KeyRangeAllocator.class);
        when(allocator.allocate())
                .thenThrow(ShortKeyGenerationException.exhausted())
                .thenReturn(new NumberRange(100, 102));
        RangeNumberIssuer issuer = new RangeNumberIssuer(allocator);

        assertThatThrownBy(issuer::next).isInstanceOf(ShortKeyGenerationException.class);

        assertThat(issuer.next()).isEqualTo(100);
        assertThat(issuer.next()).isEqualTo(101);
    }

    @Test
    void allocatesNextBlockWhenExhausted() {
        KeyRangeAllocator allocator = mock(KeyRangeAllocator.class);
        when(allocator.allocate())
                .thenReturn(new NumberRange(0, 2))
                .thenReturn(new NumberRange(50, 52));
        RangeNumberIssuer issuer = new RangeNumberIssuer(allocator);

        assertThat(issuer.next()).isEqualTo(0);
        assertThat(issuer.next()).isEqualTo(1);
        assertThat(issuer.next()).isEqualTo(50);
        assertThat(issuer.next()).isEqualTo(51);
    }
}
