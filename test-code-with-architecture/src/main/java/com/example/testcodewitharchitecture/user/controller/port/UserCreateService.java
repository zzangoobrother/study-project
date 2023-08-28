package com.example.testcodewitharchitecture.user.controller.port;

import com.example.testcodewitharchitecture.user.domain.User;
import com.example.testcodewitharchitecture.user.domain.UserCreate;
import com.example.testcodewitharchitecture.user.domain.UserUpdate;

public interface UserCreateService {

    User create(UserCreate userCreate);
}
