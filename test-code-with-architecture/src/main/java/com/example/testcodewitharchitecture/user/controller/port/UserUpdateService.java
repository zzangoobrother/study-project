package com.example.testcodewitharchitecture.user.controller.port;

import com.example.testcodewitharchitecture.user.domain.User;
import com.example.testcodewitharchitecture.user.domain.UserUpdate;

public interface UserUpdateService {

    User update(long id, UserUpdate userUpdate);
}
