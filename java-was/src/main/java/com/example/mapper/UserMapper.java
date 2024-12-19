package com.example.mapper;

import com.example.database.UserVO;
import com.example.model.User;

public class UserMapper {

    public static UserVO toUserVO(User user) {
        return new UserVO(
                user.getUserId(),
                user.getuserna
                
        )
    }
}
