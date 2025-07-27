package com.example.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTest {

    @Test
    void equality() {
        Email email1 = new Email("choi@gmail.com");
        Email email2 = new Email("choi@gmail.com");

        assertThat(email1).isEqualTo(email2);
    }
}
