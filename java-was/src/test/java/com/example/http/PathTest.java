package com.example.http;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathTest {

    @Test
    void 경로가_null일_경우_예외_발생() {
        String path = null;

        assertThatThrownBy(() -> Path.of(path))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Path는 null이거나 비어있을 수 없습니다.");
    }

    @Test
    void 경고가_비어있는_경우_예외_발생() {
        String path = "";

        assertThatThrownBy(() -> Path.of(path))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Path는 null이거나 비어있을 수 없습니다.");
    }

    @Test
    void 경로가_여러_개의_슬래시로_시작하는_경우_정규화() {
        String path = "/users//profile///123/";

        Path result = Path.of(path);

        assertEquals("/users/profile/123", result.getBasePath());
        assertEquals(List.of("users", "profile", "123"), result.getSegments());
    }

    @Test
    void 경로가_슬래시로_끝나는_경우_정규화() {
        String path = "/users/profile/123";

        Path result = Path.of(path);

        assertEquals("/users/profile/123", result.getBasePath());
    }

    @Test
    void 경로가_슬래시로_시작하지_않는_경우_정규화() {
        String path = "users/profile/123";

        Path result = Path.of(path);

        assertEquals("/users/profile/123", result.getBasePath());
    }

    @Test
    void 경로의_세그먼트_파싱() {
        String path = "/users/profile/123";

        Path result = Path.of(path);

        assertEquals(List.of("users", "profile", "123"), result.getSegments());
    }

    @Test
    void 쿼리_파라미터_파싱() {
        String path = "/users/profile/123?name=hong&age=20";

        Path result = Path.of(path);

        assertEquals(Map.of("name", "hong", "age", "20"), result.getQueryParameters());
    }

    @Test
    void 쿼리_파라미터가_없는_경우_빈_map_반환() {
        String path = "/users/profile/123";

        Path result = Path.of(path);

        assertTrue(result.getQueryParameters().isEmpty());
    }

    @Test
    void 쿼리_파라미터가_없는_디코딩() {
        String path = "/users/profile/123?name=hong%20Dong&age=20";

        Path result = Path.of(path);

        assertEquals(Map.of("name", "hong Dong", "age", "20"), result.getQueryParameters());
    }

    @Test
    void 경로의_기본적인_유효성_검사() {
        String path = "/users/profile/123";

        Path result = Path.of(path);

        assertEquals("/users/profile/123", result.getBasePath());
    }

    @Test
    void 특수_문자_포함_경로의_유효성_검사() {
        String path = "/users/prof ile/12 3/";

        Path result = Path.of(path);

        assertEquals("/users/prof ile/12 3", result.getBasePath());
    }

    @Test
    void 쿼리_파라미터가_없는_경로의_유효성_검사() {
        String path = "/users/profile/123";

        Path result = Path.of(path);

        assertEquals("/users/profile/123", result.getBasePath());
        assertTrue(result.getQueryParameters().isEmpty());
    }

    @Test
    void 한_개의_쿼리_파라미터가_있는_경로의_유효성_검사() {
        String path = "/users/profile/123?name=hong";

        Path result = Path.of(path);

        assertEquals(Map.of("name", "hong"), result.getQueryParameters());
    }

    @Test
    void 잘못된_쿼리_파라미터가_형식의_경로_처리() {
        String path = "/users/profile/123?name=hong&age";

        Path result = Path.of(path);

        assertTrue(result.getQueryParameters().containsKey("name"));
        assertTrue(result.getQueryParameters().containsKey("age"));
    }

    @Test
    void 복수의_쿼리_파라미터가_있는_경로의_유효성_검사() {
        String path = "/users/profile/123?name=hong&age=20&city=New%20York";

        Path result = Path.of(path);

        assertEquals(Map.of("name", "hong", "age", "20", "city", "New York"), result.getQueryParameters());
    }

    @Test
    void 경로에_포함된_유니코드_문자의_처리() {
        String path = "/users/profile/123";

        Path result = Path.of(path);

        assertEquals("/users/profile/123", result.getBasePath());
        assertEquals(List.of("users", "profile", "123"), result.getSegments());
    }

    @Test
    void 경로에_포함된_공백_문자의_처리() {
        String path = "/users/pro file/123";

        Path result = Path.of(path);

        assertEquals("/users/pro file/123", result.getBasePath());
        assertEquals(List.of("users", "pro file", "123"), result.getSegments());
    }

    @Test
    void 복잡한_쿼리_파라미터가_있는_경로의_유효성_검사() {
        String path = "/search?query=hong+dong&sort=desc&filters=name,age,location";

        Path result = Path.of(path);

        assertEquals(Map.of("query", "hong dong", "sort", "desc", "filters", "name,age,location"), result.getQueryParameters());
    }

    @Test
    void 잘못된_URL_인코딩_쿼리_파라미터_처리() {
        String path = "/users/profile/123?name=hong%2";

        assertThatThrownBy(() -> Path.of(path))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("쿼리 파라미터를 디코딩하는 중 오류가 발생했습니다.");
    }

    @Test
    void 빈_쿼리_파라미터_처리() {
        String path = "/users/profile/123?name=&age=20";

        Path result = Path.of(path);

        assertEquals(Map.of("name", "", "age", "20"), result.getQueryParameters());
    }
}
