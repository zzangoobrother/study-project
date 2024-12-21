package com.example.mapper;

import com.example.database.UserVO;
import com.example.model.User;

public class UserMapper {

    public static UserVO toUserVO(User user) {
        return new UserVO(
                user.getUserId(),
                user.getName(),
                user.getPassword(),
                user.getNickname(),
                user.getEmail(),
                null
        );
    }

    public static User toUser(UserVO userVO) {
        User user = new User(userVO.email(), userVO.password(), userVO.username(), userVO.nickname());
        user.initUserPk(userVO.userId());
        return user;
    }
}
