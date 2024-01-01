package com.project.adminboard.repository;

import com.project.adminboard.domain.UserAccount;
import com.project.adminboard.domain.constant.RoleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JPA 연결 테스트")
@Import(JpaRepositoryTest.TestJpaConfig.class)
@DataJpaTest
class JpaRepositoryTest {

    private final UserAccountRepository userAccountRepository;

    public JpaRepositoryTest(@Autowired UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @DisplayName("회원 정보 select 테스트")
    @Test
    void giveUserAccounts_whenSelecting_thenWorksFine() {
        List<UserAccount> userAccounts = userAccountRepository.findAll();

        assertThat(userAccounts).isNotNull().hasSize(4);
    }

    @DisplayName("회원 정보 insert 테스트")
    @Test
    void giveUserAccounts_whenInserting_thenWorksFine() {
        long previousCount = userAccountRepository.count();
        UserAccount userAccount = UserAccount.of("test", "pw", Set.of(RoleType.DEVELOPER), null, null, null);

        userAccountRepository.save(userAccount);

        assertThat(userAccountRepository.count()).isEqualTo(previousCount + 1);
    }

    @DisplayName("회원 정보 update 테스트")
    @Test
    void giveUserAccountAndRoleType_whenUpdating_thenWorksFine() {
        UserAccount userAccount = userAccountRepository.getReferenceById("choi");
        userAccount.addRoleType(RoleType.DEVELOPER);
        userAccount.addRoleType(List.of(RoleType.USER, RoleType.USER));
        userAccount.removeRoleType(RoleType.ADMIN);

        UserAccount updateAccount = userAccountRepository.saveAndFlush(userAccount);

        assertThat(updateAccount).hasFieldOrPropertyWithValue("userId", "choi")
                .hasFieldOrPropertyWithValue("roleTypes", Set.of(RoleType.DEVELOPER, RoleType.USER));
    }

    @DisplayName("회원 정보 delete 테스트")
    @Test
    void giveUserAccount_whenDeleting_thenWorksFine() {
        long previousCount = userAccountRepository.count();
        UserAccount userAccount = userAccountRepository.getReferenceById("choi");

        userAccountRepository.delete(userAccount);

        assertThat(userAccountRepository.count()).isEqualTo(previousCount - 1);
    }

    @EnableJpaAuditing
    @TestConfiguration
    static class TestJpaConfig {
        @Bean
        AuditorAware<String> auditorAware() {
            return () -> Optional.of("choi");
        }
    }
}
