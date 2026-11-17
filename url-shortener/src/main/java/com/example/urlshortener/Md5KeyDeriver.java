package com.example.urlshortener;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 원본 URL 의 MD5 해시에서 Base62 고정 길이 키를 도출한다.
 * 같은 (url, n) 은 항상 같은 키를 낸다. n 은 충돌 시 재시도 횟수로, n > 0 이면 URL 뒤에 "#n" 을 붙여 해시한다.
 */
@Component
class Md5KeyDeriver {

    private static final BigInteger BASE = BigInteger.valueOf(Base62.ALPHABET.length());

    private final int keyLength;

    Md5KeyDeriver(@Value("${shortener.md5.key-length:7}") int keyLength) {
        // short_key 컬럼이 VARCHAR(16) 이다
        if (keyLength < 1 || keyLength > 16) {
            throw new IllegalArgumentException("keyLength must be in 1~16: " + keyLength);
        }
        this.keyLength = keyLength;
    }

    String keyOf(String url, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must not be negative: " + n);
        }
        String input = n == 0 ? url : url + "#" + n;
        // MessageDigest 는 스레드 안전하지 않으므로 호출마다 새로 만든다
        byte[] digest = newMd5().digest(input.getBytes(StandardCharsets.UTF_8));
        BigInteger value = new BigInteger(1, digest);

        // 하위 keyLength 자리만 취하면 mod 62^L 과 같다. 62^16 은 long 범위를 넘으므로 BigInteger 로 계산한다.
        // 자릿수를 정확히 keyLength 번 뽑으므로 앞자리는 자연스럽게 '0' 으로 채워진다.
        char[] key = new char[keyLength];
        for (int i = keyLength - 1; i >= 0; i--) {
            BigInteger[] qr = value.divideAndRemainder(BASE);
            key[i] = Base62.ALPHABET.charAt(qr[1].intValue());
            value = qr[0];
        }
        return new String(key);
    }

    private static MessageDigest newMd5() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            // 모든 JVM 은 MD5 를 지원해야 한다
            throw new IllegalStateException("MD5 not available", e);
        }
    }
}
