package com.example.myframework2.mvc.board.web.user;

import com.example.myframework2.mvc.board.dao.UserDao;
import com.example.myframework2.mvc.board.model.User;
import com.example.myframework2.mvc.core.annotation.Inject;
import com.example.myframework2.mvc.core.web.mvc.AbstractController;
import com.example.myframework2.mvc.core.web.view.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ProfileController extends AbstractController {
    private UserDao userDao;

    @Override
    public ModelAndView execute(HttpServletRequest request, HttpServletResponse response) {
        String userId = request.getParameter("userId");

        User user = userDao.findByUserId(userId);
        if (user == null) {
            throw new NullPointerException("사용자를 찾을 수 없습니다.");
        }

        return jspView("/user/profile.jsp")
                .addObject("user", user);
    }
}
