package com.example.urlshortener;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RandomShortUrlCreatorTest extends IntegrationTestSupport {

    @Autowired
    ShortUrlCreator creator;

    @Autowired
    PlatformTransactionManager txManager;

    @MockitoBean
    RandomKeyGenerator keyGenerator;

    @MockitoSpyBean
    ShortUrlRepository repository;

    private ResultActions postUrl(String url) throws Exception {
        return mockMvc.perform(post("/api/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + url + "\"}"));
    }

    @Test
    void randomIsDefaultStrategy() {
        assertThat(creator).isInstanceOf(RandomShortUrlCreator.class);
    }

    @Test
    void createsWithGeneratedKey() throws Exception {
        given(keyGenerator.generate()).willReturn("rndAAA1");

        postUrl("https://example.com/a")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortKey").value("rndAAA1"));

        mockMvc.perform(get("/rndAAA1"))
                .andExpect(status().isFound());
    }

    @Test
    void precheckCollisionRetries() throws Exception {
        repository.saveAndFlush(ShortUrl.create("https://example.com/first", "rndBBB1"));
        given(keyGenerator.generate()).willReturn("rndBBB1", "rndBBB2");

        postUrl("https://example.com/second")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortKey").value("rndBBB2"));
    }

    @Test
    void raceCollisionRetriesAndKeepsExistingRow() throws Exception {
        repository.saveAndFlush(ShortUrl.create("https://example.com/original", "rndCCC1"));
        // 준비 단계의 저장 호출은 검증 대상에서 제외한다
        clearInvocations(repository);
        // 사전 확인을 통과했다고 가정한다 (그 사이 다른 요청이 같은 키를 넣은 상황)
        willReturn(false).given(repository).existsByShortKey("rndCCC1");
        given(keyGenerator.generate()).willReturn("rndCCC1", "rndCCC2");

        ResultActions result = postUrl("https://example.com/new").andExpect(status().isCreated());
        assertThat((String) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.shortKey"))
                .isEqualTo("rndCCC2");

        // 기존 행이 덮어써지지 않는다
        mockMvc.perform(get("/rndCCC1"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/original"));
        // UNIQUE 위반 경로를 실제로 탔는지 확인한다 (1회 실패 + 1회 성공)
        verify(repository, times(2)).saveAndFlush(any());
    }

    @Test
    void raceCollisionRetriesEvenInsideOuterTransaction() {
        repository.saveAndFlush(ShortUrl.create("https://example.com/original", "rndEEE1"));
        clearInvocations(repository);
        willReturn(false).given(repository).existsByShortKey("rndEEE1");
        given(keyGenerator.generate()).willReturn("rndEEE1", "rndEEE2");

        // 호출자에 바깥 트랜잭션이 있어도 UNIQUE 위반이 바깥을 rollback-only 로 만들지 않아야 한다
        String key = new TransactionTemplate(txManager)
                .execute(status -> creator.create("https://example.com/outer"));

        assertThat(key).isEqualTo("rndEEE2");
        verify(repository, times(2)).saveAndFlush(any());
    }

    @Test
    void exhaustedAttemptsReturns503() throws Exception {
        repository.saveAndFlush(ShortUrl.create("https://example.com/taken", "rndDDD1"));
        given(keyGenerator.generate()).willReturn("rndDDD1");
        long before = repository.count();

        postUrl("https://example.com/never").andExpect(status().isServiceUnavailable());

        assertThat(repository.count()).isEqualTo(before);
        verify(keyGenerator, times(5)).generate();
    }
}
