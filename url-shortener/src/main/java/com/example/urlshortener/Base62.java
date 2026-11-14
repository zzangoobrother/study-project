package com.example.urlshortener;

/**
 * 0-9, a-z, A-Z 순서의 62진수 인코더/디코더.
 */
final class Base62 {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length();

    private Base62() {
    }

    static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must not be negative: " + value);
        }
        if (value == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }
        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            sb.append(ALPHABET.charAt((int) (value % BASE)));
            value /= BASE;
        }
        return sb.reverse().toString();
    }

    static long decode(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("key must not be empty");
        }
        long result = 0;
        for (int i = 0; i < key.length(); i++) {
            int digit = ALPHABET.indexOf(key.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException("invalid character: " + key.charAt(i));
            }
            result = Math.addExact(Math.multiplyExact(result, BASE), digit);
        }
        return result;
    }
}
