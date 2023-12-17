package com.example.myframework2.mvc.board.web.user;

import com.example.myframework2.mvc.core.mvc.Controller;
import com.example.myframework2.mvc.core.mvc.JspView;
import com.example.myframework2.mvc.core.mvc.View;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginFormController implements Controller {
    @Override
    public View execute(HttpServletRequest request, HttpServletResponse response) {
        return new JspView("/user/login.jsp");
    }
}
