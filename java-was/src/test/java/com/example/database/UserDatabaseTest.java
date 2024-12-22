package com.example.database;

import com.example.application.database.UserDatabase;
import com.example.application.domain.user.model.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UserDatabaseTest {

    @Test
    void userDatabase에_save할_때_userID가_존재하면_예외를_던진다() {
        UserDatabase userDatabase = new UserDatabase();

        User user1 = new User("email", "userId", "password", "name");
        User user2 = new User("email2", "userId", "pasdf", "name2");

        userDatabase.save(user1);

        assertThatThrownBy(() -> userDatabase.save(user2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 존재하는 userId 입니다.");
    }

    @Test
    void userDatabase에_save할_때_userID가_없으면_저장에_성공한다() {
        UserDatabase userDatabase = new UserDatabase();

        User user1 = new User("email", "userId", "password", "name");

        long save = userDatabase.save(user1);

        assertEquals(1, save);
    }
}
