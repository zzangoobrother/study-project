package com.example.model.business.user;

import com.example.authorization.AuthorizationContext;
import com.example.authorization.AuthorizationContextHolder;
import com.example.database.Database;
import com.example.database.UserDatabase;
import com.example.http.Session;
import com.example.model.User;
import com.example.processor.Triggerable;
import com.example.web.user.response.UserInfoResponse;

public class GetUserInfoLogic implements Triggerable<Void, UserInfoResponse> {

    private final Database<User> userDatabase;

    public GetUserInfoLogic(Database<User> userDatabase) {
        this.userDatabase = userDatabase;
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

        Long userPk = session.getUserPk();
        User user = userDatabase.findById(userPk).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return new UserInfoResponse(user.getName());
    }
}
