package com.example.testcodewitharchitecture.medium;

import com.example.testcodewitharchitecture.user.domain.UserStatus;
import com.example.testcodewitharchitecture.user.domain.UserUpdate;
import com.example.testcodewitharchitecture.user.infrastructure.UserEntity;
import com.example.testcodewitharchitecture.user.infrastructure.JpaUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlGroup;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@SqlGroup({
        @Sql(value = "/sql/user-controller-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
        @Sql(value = "/sql/delete-all--data.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JpaUserRepository userRepository;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 사용자는_특정_유저의_정보를_개인정보는_소거된_전달_받을_수_있다() throws Exception {
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("abc@naver.com"))
                .andExpect(jsonPath("$.nickname").value("abc"))
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void 사용자는_존재하지_않는_유저의_아이디로_api_호출할_경우_404_응답을_받는다() throws Exception {
        mockMvc.perform(get("/api/users/12345"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Users에서 ID 12345를 찾을 수 없습니다."));
    }

    @Test
    void 사용자는_인증_코드로_계정을_활성화_시킬_수_있다() throws Exception {
        mockMvc.perform(get("/api/users/1/verify")
                        .queryParam("certificationCode", "aaaaaaaa-aaaa-aaaa-aaaaaaaaaa"))
                .andExpect(status().isFound());

        UserEntity userEntity = userRepository.findById(1L).orElseThrow();
        assertThat(userEntity.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void 사용자는_인증_코드로_일치하지_않을_경우_권한_없음_에러를_내려준다() throws Exception {
        mockMvc.perform(get("/api/users/1/verify")
                        .queryParam("certificationCode", "aaaaaaaa-aaaa-aaaa-aaaaaaaaab"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 사용자는_내_정보를_불러올_때_개인정보인_주소도_갖고_올_수_있다() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("EMAIL", "abc@naver.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("abc@naver.com"))
                .andExpect(jsonPath("$.nickname").value("abc"))
                .andExpect(jsonPath("$.address").value("Seoul"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void 사용자는_내_정보를_수정할_수_있다() throws Exception {
        UserUpdate userUpdate = UserUpdate.builder()
                .nickname("aaa")
                .address("Pang")
                .build();

        mockMvc.perform(put("/api/users/me")
                        .header("EMAIL", "abc@naver.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userUpdate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("abc@naver.com"))
                .andExpect(jsonPath("$.nickname").value("aaa"))
                .andExpect(jsonPath("$.address").value("Pang"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}