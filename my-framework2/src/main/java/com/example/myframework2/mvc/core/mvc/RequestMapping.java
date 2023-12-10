package com.example.myframework2.mvc.core.mvc;

import com.example.myframework2.mvc.board.web.*;

import java.util.HashMap;
import java.util.Map;

public class RequestMapping {
    private Map<String, Controller> controllers = new HashMap<>();

    void init() {
        controllers.put("/", new HomeController());
        controllers.put("/users/create", new CreateUserController());
        controllers.put("/users/form", new ForwardController("/user/form.jsp"));
        controllers.put("/user/list", new ListUserController());
        controllers.put("/users/loginForm", new ForwardController("/user/login.jsp"));
        controllers.put("/users/login", new LoginController());
        controllers.put("/users/updateForm", new UpdateUserFormController());
        controllers.put("/users/update", new UpdateUserController());
    }

    public Controller getController(String url) {
        return controllers.get(url);
    }
}
