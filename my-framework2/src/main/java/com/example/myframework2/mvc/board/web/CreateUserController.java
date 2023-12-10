package com.example.myframework2.mvc.board.web;

import com.example.myframework2.mvc.board.model.User;
import com.example.myframework2.mvc.core.db.DataBase;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CreateUserController implements Controller {

    @Override
    public String execute(HttpServletRequest request, HttpServletResponse httpServletrespon) {
        User user = new User(request.getParameter("userId"), request.getParameter("password"), request.getParameter("name"), request.getParameter("email"));
        DataBase.addUser(user);

        return "redirect:/user/list";
    }
}
