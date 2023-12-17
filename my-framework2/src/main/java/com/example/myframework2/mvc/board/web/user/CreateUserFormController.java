package com.example.myframework2.mvc.board.web.user;

import com.example.myframework2.mvc.core.mvc.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CreateUserFormController extends AbstractController {

    @Override
    public ModelAndView execute(HttpServletRequest request, HttpServletResponse response) {
        return jspView("/user/form.jsp");
    }
}
