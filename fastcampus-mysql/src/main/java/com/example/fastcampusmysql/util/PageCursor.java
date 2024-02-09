package com.example.fastcampusmysql.util;

import java.util.List;

public record PageCursor<T>(
        CusorRequest nextCursorRequest,
        List<T> body
) {
}
