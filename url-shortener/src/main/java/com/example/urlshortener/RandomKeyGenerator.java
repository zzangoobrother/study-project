package com.example.urlshortener;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Base62 문자로 이루어진 고정 길이의 무작위 키를 만든다.
 */
@Component
public class RandomKeyGenerator {

    private final RandomGenerator random;
    private final int keyLength;

    // 테스트에서 난수원과 길이를 직접 주입하기 위한 생성자
    RandomKeyGenerator(RandomGenerator random, int keyLength) {
        if (keyLength < 1) {
            throw new IllegalArgumentException("keyLength must be positive: " + keyLength);
        }
        this.random = random;
        this.keyLength = keyLength;
    }

    @Autowired
    RandomKeyGenerator(@Value("${shortener.random.key-length:7}") int keyLength) {
        this(new SecureRandom(), keyLength);
    }

    public String generate() {
        StringBuilder sb = new StringBuilder(keyLength);
        for (int i = 0; i < keyLength; i++) {
            sb.append(Base62.ALPHABET.charAt(random.nextInt(Base62.ALPHABET.length())));
        }
        return sb.toString();
    }
}
