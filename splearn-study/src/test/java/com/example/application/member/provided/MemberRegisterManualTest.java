package com.example.application.member.provided;

import com.example.application.member.MemberModifyService;
import com.example.application.member.MemberQueryService;
import com.example.application.member.required.EmailSender;
import com.example.application.member.required.MemberRepository;
import com.example.domain.member.Member;
import com.example.domain.member.MemberFixture;
import com.example.domain.member.MemberStatus;
import com.example.domain.member.Profile;
import com.example.domain.shared.Email;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class MemberRegisterManualTest {

    @Test
    void registerTestStub() {
        MemberRegister register = new MemberModifyService(new MemberQueryService(new MemberRepositoryStub()), new MemberRepositoryStub(), new EmailSenderStub(), MemberFixture.createPasswordEncoder());

        Member member = register.register(MemberFixture.createMemberRegisterRequest());
        assertThat(member.getId()).isNotNull();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.PENDING);
    }

    @Test
    void registerTestMock() {
        EmailSenderMock emailSenderMock = new EmailSenderMock();
        MemberRegister register = new MemberModifyService(new MemberQueryService(new MemberRepositoryStub()), new MemberRepositoryStub(), emailSenderMock, MemberFixture.createPasswordEncoder());

        Member member = register.register(MemberFixture.createMemberRegisterRequest());
        assertThat(member.getId()).isNotNull();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.PENDING);

        assertThat(emailSenderMock.getTos()).hasSize(1);
        assertThat(emailSenderMock.getTos().get(0)).isEqualTo(member.getEmail());
    }

    @Test
    void registerTestMockito() {
        EmailSender emailSenderMock = Mockito.mock(EmailSender.class);

        MemberRegister register = new MemberModifyService(new MemberQueryService(new MemberRepositoryStub()), new MemberRepositoryStub(), emailSenderMock, MemberFixture.createPasswordEncoder());

        Member member = register.register(MemberFixture.createMemberRegisterRequest());
        assertThat(member.getId()).isNotNull();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.PENDING);

        Mockito.verify(emailSenderMock).send(eq(member.getEmail()), any(), any());
    }

    static class MemberRepositoryStub implements MemberRepository {
        @Override
        public Member save(Member member) {
            ReflectionTestUtils.setField(member, "id", 1L);
            return member;
        }

        @Override
        public Optional<Member> findByEmail(Email email) {
            return Optional.empty();
        }

        @Override
        public Optional<Member> findById(Long memberId) {
            return Optional.empty();
        }

        @Override
        public Optional<Member> findByProfile(Profile profile) {
            return Optional.empty();
        }


    }

    static class EmailSenderStub implements EmailSender {
        @Override
        public void send(Email email, String subjext, String body) {
        }
    }

    static class EmailSenderMock implements EmailSender {
        List<Email> tos = new ArrayList<>();
        @Override
        public void send(Email email, String subject, String body) {
            tos.add(email);
        }

        public List<Email> getTos() {
            return tos;
        }
    }
}
