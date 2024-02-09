package com.example.fastcampusmysql.util;

public record CusorRequest(
        Long key,
        int size
) {
    public static final Long NONE_KEY = -1L;

    public Boolean hasKey() {
        return key != null;
    }

    public CusorRequest next(Long key) {
        return new CusorRequest(key, size);
    }
}
