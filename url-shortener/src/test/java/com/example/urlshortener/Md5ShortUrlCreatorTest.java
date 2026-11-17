package com.example.urlshortener;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 키 길이 3 으로 해시 충돌을 쉽게 만든다
@TestPropertySource(properties = {
        "shortener.key-strategy=md5",
        "shortener.md5.key-length=3",
        "shortener.md5.max-attempts=5"
})
class Md5ShortUrlCreatorTest extends IntegrationTestSupport {

    private static final String PREFIX = "https://md5test.example/";

    @MockitoSpyBean
    ShortUrlRepository repository;

    @Autowired
    Md5KeyDeriver deriver;

    // 테스트가 만들거나 넣은 키 (정리 대상)
    private final List<String> usedKeys = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        org.mockito.Mockito.reset(repository);
        for (String key : usedKeys) {
            repository.findByShortKey(key).ifPresent(repository::delete);
        }
        usedKeys.clear();
    }

    private ResultActions postUrl(String url) throws Exception {
        return mockMvc.perform(post("/api/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + url + "\"}"));
    }

    private String keyOfResponse(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.shortKey");
    }

    // 사용할 키를 등록하고, 비어 있지 않으면 테스트를 건너뛴다
    private void useKeys(String... keys) {
        for (String key : keys) {
            usedKeys.add(key);
            assumeTrue(repository.findByShortKey(key).isEmpty());
        }
    }

    // 첫 조회만 "없음", 이후는 실제 DB 를 본다.
    // 스파이가 인터페이스 프록시를 감싸므로 doCallRealMethod 는 abstract 메서드 오류가 난다.
    // 대신 스텁되지 않은 findAll 로 실제 DB 를 조회해 같은 효과를 낸다.
    private void stubFirstLookupEmpty(String key) {
        doReturn(Optional.empty())
                .doAnswer(inv -> repository.findAll().stream()
                        .filter(s -> key.equals(s.getShortKey()))
                        .findFirst())
                .when(repository).findByShortKey(key);
    }

    private long countRowsOf(String url) {
        return repository.findAll().stream().filter(s -> s.getOriginalUrl().equals(url)).count();
    }

    @Test
    void sameUrlReturnsSameKeyAndSingleRow() throws Exception {
        String url = PREFIX + "sameUrl/a";
        String k0 = deriver.keyOf(url, 0);
        useKeys(k0);

        String first = keyOfResponse(postUrl(url).andExpect(status().isCreated()));
        String second = keyOfResponse(postUrl(url).andExpect(status().isCreated()));

        assertThat(first).isEqualTo(second).isEqualTo(k0);
        assertThat(countRowsOf(url)).isEqualTo(1);
    }

    @Test
    void collisionMovesToNextSalt() throws Exception {
        // 키 0번 해시가 같은 서로 다른 두 URL 을 찾는다
        Map<String, String> seen = new HashMap<>();
        String u1 = null;
        String u2 = null;
        for (int i = 0; u2 == null; i++) {
            String candidate = PREFIX + "collision/" + i;
            String prev = seen.put(deriver.keyOf(candidate, 0), candidate);
            if (prev != null) {
                u1 = prev;
                u2 = candidate;
            }
        }
        String k1Of1 = deriver.keyOf(u1, 0);
        String k1Of2 = deriver.keyOf(u2, 1);
        useKeys(k1Of1, k1Of2);

        assertThat(keyOfResponse(postUrl(u1).andExpect(status().isCreated()))).isEqualTo(k1Of1);
        assertThat(keyOfResponse(postUrl(u2).andExpect(status().isCreated()))).isEqualTo(k1Of2);
        assertThat(keyOfResponse(postUrl(u2).andExpect(status().isCreated()))).isEqualTo(k1Of2);

        mockMvc.perform(get("/" + k1Of1))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, u1));
        mockMvc.perform(get("/" + k1Of2))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, u2));
    }

    @Test
    void urlComparisonIsCaseSensitive() throws Exception {
        String upper = PREFIX + "case/CaseA";
        String lower = PREFIX + "case/casea";
        useKeys(deriver.keyOf(upper, 0), deriver.keyOf(upper, 1), deriver.keyOf(lower, 0), deriver.keyOf(lower, 1));

        String upperKey = keyOfResponse(postUrl(upper).andExpect(status().isCreated()));
        String lowerKey = keyOfResponse(postUrl(lower).andExpect(status().isCreated()));

        assertThat(upperKey).isNotEqualTo(lowerKey);
        mockMvc.perform(get("/" + upperKey))
                .andExpect(header().string(HttpHeaders.LOCATION, upper));
        mockMvc.perform(get("/" + lowerKey))
                .andExpect(header().string(HttpHeaders.LOCATION, lower));
    }

    @Test
    void raceOnSameUrlReturnsSameKey() throws Exception {
        String url = PREFIX + "raceSame/a";
        String k0 = deriver.keyOf(url, 0);
        useKeys(k0);
        // 상대 요청이 먼저 같은 URL 을 넣은 상황
        repository.saveAndFlush(ShortUrl.create(url, k0));
        // 첫 조회만 "없음", 이후는 실제 조회
        stubFirstLookupEmpty(k0);

        assertThat(keyOfResponse(postUrl(url).andExpect(status().isCreated()))).isEqualTo(k0);
        assertThat(countRowsOf(url)).isEqualTo(1);
    }

    @Test
    void raceWithDifferentUrlMovesOn() throws Exception {
        String url = PREFIX + "raceDiff/a";
        String other = PREFIX + "race-other";
        String k0 = deriver.keyOf(url, 0);
        String k1 = deriver.keyOf(url, 1);
        useKeys(k0, k1);
        repository.saveAndFlush(ShortUrl.create(other, k0));
        stubFirstLookupEmpty(k0);

        assertThat(keyOfResponse(postUrl(url).andExpect(status().isCreated()))).isEqualTo(k1);
        // 기존 행이 덮어써지지 않는다
        mockMvc.perform(get("/" + k0))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, other));
    }

    @Test
    void exhaustedChainReturns503() throws Exception {
        String url = PREFIX + "exhausted/a";
        String[] keys = new String[5];
        for (int n = 0; n < 5; n++) {
            keys[n] = deriver.keyOf(url, n);
        }
        useKeys(keys);
        for (int n = 0; n < 5; n++) {
            repository.saveAndFlush(ShortUrl.create(PREFIX + "exhausted/other" + n, keys[n]));
        }
        long before = repository.count();

        postUrl(url).andExpect(status().isServiceUnavailable());

        assertThat(repository.count()).isEqualTo(before);
    }
}
