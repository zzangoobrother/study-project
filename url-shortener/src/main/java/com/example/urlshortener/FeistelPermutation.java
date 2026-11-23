package com.example.urlshortener;

/**
 * 번호 공간 [0, domainSize) 를 일대일로 섞는 Feistel 순열.
 *
 * <p>카운터가 주는 연속 번호를 그대로 Base62 로 바꾸면 키가 순서대로 노출되므로, 번호를 한 번 섞은 뒤 인코딩한다.
 * 순열이라서 서로 다른 입력은 항상 서로 다른 출력을 갖고, {@link #inverse(long)} 로 원래 번호를 복원할 수 있다.
 *
 * <ul>
 *   <li>62^7 은 2의 거듭제곱이 아니라서 42비트 Feistel 결과가 N 이상일 수 있다. 그때 결과를 다시 섞는 것이
 *       cycle walking 이고, 평균 반복은 약 1.25회다.
 *   <li>목적은 보안이 아니라 발급 순서를 예측하지 못하게 하는 것이다.
 *   <li>secret 을 배포 후 바꾸면 순열이 달라져 이미 발급된 키와 겹친다.
 * </ul>
 */
final class FeistelPermutation {

    /** 62^7: 7자리 Base62 키로 표현할 수 있는 번호 개수. */
    static final long SHORT_KEY_SPACE = 3_521_614_606_208L;

    private static final int SHORT_KEY_BITS = 42;
    private static final int ROUNDS = 4;
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private final long domainSize;
    private final int half;
    private final long mask;
    private final long[] roundKeys = new long[ROUNDS];

    FeistelPermutation(long domainSize, int bits, long secret) {
        if (domainSize < 1) {
            throw new IllegalArgumentException("domainSize must be positive: " + domainSize);
        }
        if (bits <= 0 || bits % 2 != 0 || bits > 62) {
            throw new IllegalArgumentException("bits must be a positive even number up to 62: " + bits);
        }
        if ((1L << bits) < domainSize) {
            throw new IllegalArgumentException("2^bits must be >= domainSize: bits=" + bits + ", domainSize=" + domainSize);
        }
        this.domainSize = domainSize;
        this.half = bits / 2;
        this.mask = (1L << half) - 1;
        for (int i = 0; i < ROUNDS; i++) {
            roundKeys[i] = mix64(secret + i * GOLDEN_GAMMA);
        }
    }

    static FeistelPermutation forShortKey(long secret) {
        return new FeistelPermutation(SHORT_KEY_SPACE, SHORT_KEY_BITS, secret);
    }

    long permute(long x) {
        requireInDomain(x);
        long y = once(x);
        while (y >= domainSize) {
            y = once(y);
        }
        return y;
    }

    long inverse(long y) {
        requireInDomain(y);
        long x = onceInverse(y);
        while (x >= domainSize) {
            x = onceInverse(x);
        }
        return x;
    }

    private long once(long x) {
        long left = x >>> half;
        long right = x & mask;
        for (int i = 0; i < ROUNDS; i++) {
            long next = left ^ roundFunction(right, roundKeys[i]);
            left = right;
            right = next;
        }
        return (left << half) | right;
    }

    private long onceInverse(long y) {
        long left = y >>> half;
        long right = y & mask;
        for (int i = ROUNDS - 1; i >= 0; i--) {
            long prev = right ^ roundFunction(left, roundKeys[i]);
            right = left;
            left = prev;
        }
        return (left << half) | right;
    }

    private long roundFunction(long value, long key) {
        return mix64(value ^ key) & mask;
    }

    private void requireInDomain(long value) {
        if (value < 0 || value >= domainSize) {
            throw new IllegalArgumentException("value must be in [0, " + domainSize + "): " + value);
        }
    }

    /** SplitMix64 finalizer. */
    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
