package com.example.myframework2.mvc.board.web.qna;

import com.example.myframework2.mvc.board.dao.QuestionDao;
import com.example.myframework2.mvc.board.model.Question;
import com.example.myframework2.mvc.board.model.User;
import com.example.myframework2.mvc.board.util.UserSessionUtils;
import com.example.myframework2.mvc.core.mvc.AbstractController;
import com.example.myframework2.mvc.core.mvc.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CreateQuestionController extends AbstractController {
    @Override
    public ModelAndView execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
        if (!UserSessionUtils.isLogined(request.getSession())) {
            return jspView("redirect:/users/loginForm");
        }

        User user = UserSessionUtils.getUserSession(request.getSession());
        Question question = new Question(user.getUserId(), request.getParameter("title"), request.getParameter("contents"));

        QuestionDao questionDao = new QuestionDao();
        questionDao.insert(question);

        return jspView("redirect:/");
    }
}
