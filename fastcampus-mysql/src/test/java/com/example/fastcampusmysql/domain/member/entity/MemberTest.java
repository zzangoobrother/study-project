package com.example.fastcampusmysql.domain.member.entity;

import com.example.fastcampusmysql.util.MemberFixtureFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemberTest {

    @DisplayName("회원은 닉네임을 변경할 수 있다.")
    @Test
    void testChangeName() {
        var member = MemberFixtureFactory.create();
        var expected = "pnu";

        member.changeNickname(expected);

        assertEquals(expected, member.getNickname());
    }

    @DisplayName("사용하는 닉네임은 10자를 초과할 수 없다.")
    @Test
    void testNicknameMaxLength() {
        var member = MemberFixtureFactory.create();
        var overMaxLengthName = "pnupnupnupnu";

        assertThrows(IllegalArgumentException.class, () -> member.changeNickname(overMaxLengthName));
    }
}
