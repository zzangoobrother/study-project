package com.example.application.domain.user.business;

import com.example.webserver.authorization.AuthorizationContext;
import com.example.webserver.authorization.AuthorizationContextHolder;
import com.example.application.database.UserVO;
import com.example.application.database.dao.UserDao;
import com.example.webserver.http.Session;
import com.example.application.processor.Triggerable;
import com.example.application.domain.user.response.UserInfoResponse;

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
