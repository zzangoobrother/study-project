package com.project.adminboard.repository;

import com.project.adminboard.domain.AdminAccount;
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

    private final AdminAccountRepository adminAccountRepository;

    public JpaRepositoryTest(@Autowired AdminAccountRepository adminAccountRepository) {
        this.adminAccountRepository = adminAccountRepository;
    }

    @DisplayName("회원 정보 select 테스트")
    @Test
    void giveAdminAccounts_whenSelecting_thenWorksFine() {
        List<AdminAccount> adminAccounts = adminAccountRepository.findAll();

        assertThat(adminAccounts).isNotNull().hasSize(4);
    }

    @DisplayName("회원 정보 insert 테스트")
    @Test
    void giveAdminAccounts_whenInserting_thenWorksFine() {
        long previousCount = adminAccountRepository.count();
        AdminAccount adminAccount = AdminAccount.of("test", "pw", Set.of(RoleType.DEVELOPER), null, null, null);

        adminAccountRepository.save(adminAccount);

        assertThat(adminAccountRepository.count()).isEqualTo(previousCount + 1);
    }

    @DisplayName("회원 정보 update 테스트")
    @Test
    void giveAdminAccountAndRoleType_whenUpdating_thenWorksFine() {
        AdminAccount adminAccount = adminAccountRepository.getReferenceById("choi");
        adminAccount.addRoleType(RoleType.DEVELOPER);
        adminAccount.addRoleType(List.of(RoleType.USER, RoleType.USER));
        adminAccount.removeRoleType(RoleType.ADMIN);

        AdminAccount updateAccount = adminAccountRepository.saveAndFlush(adminAccount);

        assertThat(updateAccount).hasFieldOrPropertyWithValue("userId", "choi")
                .hasFieldOrPropertyWithValue("roleTypes", Set.of(RoleType.DEVELOPER, RoleType.USER));
    }

    @DisplayName("회원 정보 delete 테스트")
    @Test
    void giveAdminAccount_whenDeleting_thenWorksFine() {
        long previousCount = adminAccountRepository.count();
        AdminAccount adminAccount = adminAccountRepository.getReferenceById("choi");

        adminAccountRepository.delete(adminAccount);

        assertThat(adminAccountRepository.count()).isEqualTo(previousCount - 1);
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
