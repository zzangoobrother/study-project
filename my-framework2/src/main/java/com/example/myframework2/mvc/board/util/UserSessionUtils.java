package com.example.myframework2.mvc.board.util;

import com.example.myframework2.mvc.board.model.User;

import javax.servlet.http.HttpSession;

public class UserSessionUtils {

    public static User getUserSession(HttpSession session) {
        Object value = session.getAttribute("user");
        if (value == null) {
            return null;
        }

        return (User) value;
    }

    public static boolean isLogined(HttpSession session) {
        if (getUserSession(session) == null) {
            return false;
        }
        return true;
    }

    public static boolean isSameUser(HttpSession session, User target) {
        if (!isLogined(session)) {
            return false;
        }

        if (target == null) {
            return false;
        }

        return target.equals(getUserSession(session));
    }
}
