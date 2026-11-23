package com.example.urlshortener;

// [start, end) 반열린 구간의 번호 범위
record NumberRange(long start, long end) {

    NumberRange {
        if (start >= end) {
            throw new IllegalArgumentException("start 는 end 보다 작아야 합니다. start=" + start + ", end=" + end);
        }
    }
}
