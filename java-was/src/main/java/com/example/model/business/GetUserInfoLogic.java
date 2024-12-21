package com.example.model.business;

import com.example.authorization.AuthorizationContext;
import com.example.authorization.AuthorizationContextHolder;
import com.example.database.UserVO;
import com.example.database.dao.UserDao;
import com.example.http.Session;
import com.example.processor.Triggerable;
import com.example.web.user.response.UserInfoResponse;

public class GetUserInfoLogic implements Triggerable<Void, UserInfoResponse> {

    private final UserDao userDao;

    public GetUserInfoLogic(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public UserInfoResponse run(Void unused) {
        return getUserInfo();
    }

    private UserInfoResponse getUserInfo() {
        AuthorizationContext context = AuthorizationContextHolder.getContextHolder();
        if (context == null) {
            throw new RuntimeException("로그인이 필요합니다.");
        }
        Session session = context.getSession();

        Long userPk = session.getUserId();
        UserVO userVO = userDao.findById(userPk).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return new UserInfoResponse(userVO.nickname());
    }
}
