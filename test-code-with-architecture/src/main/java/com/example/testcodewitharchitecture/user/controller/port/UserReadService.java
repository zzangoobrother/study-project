package com.example.testcodewitharchitecture.user.controller.port;

import com.example.testcodewitharchitecture.user.domain.User;
import com.example.testcodewitharchitecture.user.domain.UserCreate;
import com.example.testcodewitharchitecture.user.domain.UserUpdate;

public interface UserReadService {
    User getByEmail(String email);

    User getById(long id);
}
