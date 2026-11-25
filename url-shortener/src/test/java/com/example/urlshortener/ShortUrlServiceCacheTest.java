package com.example.urlshortener;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ShortUrlServiceCacheTest {

    private final ShortUrlCreator creator = mock(ShortUrlCreator.class);
    private final ShortUrlLookup lookup = mock(ShortUrlLookup.class);
    private final ShortUrlService service = new ShortUrlService(creator, lookup);

    @Test
    void recordsCreatedKeyAfterSuccess() {
        String url = "https://example.com/a";
        when(creator.create(url)).thenReturn("abc1234");

        assertThat(service.shorten(url)).isEqualTo("abc1234");

        verify(lookup).recordCreated("abc1234", url);
    }

    // 생성이 실패하면 L2 에 아무것도 남기지 않는다
    @Test
    void doesNotRecordWhenCreationFails() {
        String url = "https://example.com/a";
        when(creator.create(url)).thenThrow(new ShortKeyGenerationException(5));

        assertThatThrownBy(() -> service.shorten(url))
                .isInstanceOf(ShortKeyGenerationException.class);

        verify(lookup, never()).recordCreated(anyString(), anyString());
    }

    // 형식이 틀린 키는 캐시·DB 어디에도 닿지 않는다
    @Test
    void invalidKeyNeverTouchesLookup() {
        for (String key : new String[]{"ab-c", "한글", "a".repeat(17)}) {
            assertThatThrownBy(() -> service.getOriginalUrl(key))
                    .isInstanceOf(ShortUrlNotFoundException.class);
        }

        verifyNoInteractions(lookup);
    }

    @Test
    void emptyLookupIsNotFound() {
        when(lookup.findOriginalUrl("abc1234")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOriginalUrl("abc1234"))
                .isInstanceOf(ShortUrlNotFoundException.class);
    }
}
