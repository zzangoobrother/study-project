package com.example.urlshortener;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

// Spring 컨텍스트 없이 생성자 검증만 확인하는 단위 테스트
class RandomShortUrlCreatorConstructorTest {

    @Test
    void rejectsNonPositiveMaxAttempts() {
        ShortUrlRepository repository = mock(ShortUrlRepository.class);
        RandomKeyGenerator keyGenerator = mock(RandomKeyGenerator.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);

        assertThatThrownBy(() -> new RandomShortUrlCreator(repository, keyGenerator, txManager, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RandomShortUrlCreator(repository, keyGenerator, txManager, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
