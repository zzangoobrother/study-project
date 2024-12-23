package com.example.application.mapper;

import com.example.application.database.vo.UserVO;
import com.example.application.domain.user.model.User;

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
