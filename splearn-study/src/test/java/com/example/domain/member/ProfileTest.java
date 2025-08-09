package com.example.domain.member;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileTest {

    @Test
    void profile() {
        new Profile("choiss");
        new Profile("choi111");
        new Profile("12345");
        new Profile("");
    }

    @Test
    void ProfileFail() {
        assertThatThrownBy(() -> new Profile("tolongtolongtolongtolongtolong")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Profile("A")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Profile("프로필")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void url() {
        Profile profile = new Profile("choisk");

        assertThat(profile.url()).isEqualTo("@choisk");
    }
}
