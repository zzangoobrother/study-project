package myjunit.mvc.part1.webserver;

import myjunit.mvc.part1.controller.Controller;
import myjunit.mvc.part1.controller.CreateUserController;
import myjunit.mvc.part1.controller.ListUserController;
import myjunit.mvc.part1.controller.LoginController;

import java.util.HashMap;
import java.util.Map;

public class RequestMapping {
    private static Map<String, Controller> controllers = new HashMap<>();

    static {
        controllers.put("/user/create", new CreateUserController());
        controllers.put("/user/login", new LoginController());
        controllers.put("/user/list", new ListUserController());
    }

    public static Controller getController(String url) {
        return controllers.get(url);
    }
}
