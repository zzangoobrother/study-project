package com.example.myframework2.mvc.board.web;

import java.util.HashMap;
import java.util.Map;

public class RequestMapping {
    private static Map<String, Controller> controllers = new HashMap<>();

    static {
        controllers.put("/users/create", new CreateUserController());
        controllers.put("/users/form", new CreateUserFormController());
        controllers.put("/user/list", new ListUserController());
        controllers.put("/users/loginForm", new LoginFormController());
        controllers.put("/users/login", new LoginController());
        controllers.put("/users/updateForm", new UpdateUserFormController());
        controllers.put("/users/update", new UpdateUserController());
    }

    public static Controller getController(String url) {
        return controllers.get(url);
    }
}
