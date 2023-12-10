package com.example.myframework2.mvc.core.mvc;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ForwardController implements Controller {

    private String forwardUrl;

    public ForwardController(String forwardUrl) {
        this.forwardUrl = forwardUrl;
    }

    @Override
    public String execute(HttpServletRequest request, HttpServletResponse httpServletrespon) {
        return forwardUrl;
    }
}
