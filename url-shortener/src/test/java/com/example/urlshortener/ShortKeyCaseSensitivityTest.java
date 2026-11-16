package com.example.urlshortener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShortKeyCaseSensitivityTest extends IntegrationTestSupport {

    @Autowired
    ShortUrlRepository repository;

    @Test
    void keysDifferingOnlyInCaseAreDistinct() throws Exception {
        // 대소문자만 다른 두 키가 모두 저장되어야 한다 (ascii_bin collation)
        repository.saveAndFlush(ShortUrl.create("https://example.com/lower", "csAb"));
        repository.saveAndFlush(ShortUrl.create("https://example.com/upper", "csaB"));

        mockMvc.perform(get("/csAb"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/lower"));
        mockMvc.perform(get("/csaB"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/upper"));
        assertThat(repository.existsByShortKey("CSAB")).isFalse();
    }
}
