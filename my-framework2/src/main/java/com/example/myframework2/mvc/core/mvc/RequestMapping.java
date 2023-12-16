package com.example.myframework2.mvc.core.mvc;

import com.example.myframework2.mvc.board.web.*;
import com.example.myframework2.mvc.board.web.qna.AddAnswerController;
import com.example.myframework2.mvc.board.web.qna.DeleteAnswerController;
import com.example.myframework2.mvc.board.web.qna.ShowController;
import com.example.myframework2.mvc.board.web.user.*;

import java.util.HashMap;
import java.util.Map;

public class RequestMapping {
    private Map<String, Controller> controllers = new HashMap<>();

    void init() {
        controllers.put("/", new HomeController());
        controllers.put("/users/create", new CreateUserController());
        controllers.put("/users/form", new ForwardController("/user/form.jsp"));
        controllers.put("/users", new ListUserController());
        controllers.put("/users/loginForm", new ForwardController("/user/login.jsp"));
        controllers.put("/users/login", new LoginController());
        controllers.put("/users/profile", new ProfileController());
        controllers.put("/users/logout", new LogoutController());
        controllers.put("/users/updateForm", new UpdateUserFormController());
        controllers.put("/users/update", new UpdateUserController());

        controllers.put("/qna/show", new ShowController());
        controllers.put("/api/qna/addAnswer", new AddAnswerController());
        controllers.put("/api/qna/deleteAnswer", new DeleteAnswerController());
    }

    public Controller getController(String url) {
        return controllers.get(url);
    }
}
