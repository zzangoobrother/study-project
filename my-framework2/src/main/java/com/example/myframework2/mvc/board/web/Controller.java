package com.example.myframework2.mvc.board.web;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface Controller {
    String execute(HttpServletRequest request, HttpServletResponse httpServletrespon);
}
