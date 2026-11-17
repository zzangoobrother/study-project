package com.example.urlshortener;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

// Spring 컨텍스트 없이 생성자 검증만 확인하는 단위 테스트
class Md5ShortUrlCreatorConstructorTest {

    @Test
    void rejectsNonPositiveMaxAttempts() {
        ShortUrlRepository repository = mock(ShortUrlRepository.class);
        Md5KeyDeriver deriver = mock(Md5KeyDeriver.class);
        ShortUrlInserter inserter = mock(ShortUrlInserter.class);

        assertThatThrownBy(() -> new Md5ShortUrlCreator(repository, deriver, inserter, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Md5ShortUrlCreator(repository, deriver, inserter, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
