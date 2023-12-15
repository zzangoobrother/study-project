package com.example.myframework2.mvc.board.web.user;

import com.example.myframework2.mvc.core.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CreateUserFormController implements Controller {

    @Override
    public String execute(HttpServletRequest request, HttpServletResponse httpServletrespon) {
        return "/user/form.jsp";
    }
}
