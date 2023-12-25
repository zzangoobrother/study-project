package com.example.myframework2.mvc.core.web.mvc;

import com.example.myframework2.mvc.board.dao.AnswerDao;
import com.example.myframework2.mvc.board.dao.JdbcAnswerDao;
import com.example.myframework2.mvc.board.dao.JdbcQuestionDao;
import com.example.myframework2.mvc.board.dao.QuestionDao;
import com.example.myframework2.mvc.board.service.QnaService;
import com.example.myframework2.mvc.board.web.HomeController;
import com.example.myframework2.mvc.board.web.qna.*;
import com.example.myframework2.mvc.board.web.user.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

public class LegacyHandlerMapping implements HandlerMapping {
    private Map<String, Controller> controllers = new HashMap<>();

    public void init() {
        AnswerDao answerDao = new JdbcAnswerDao();
        QuestionDao questionDao = new JdbcQuestionDao();
        QnaService qnaService = new QnaService(answerDao, questionDao);

//        controllers.put("/", new HomeController(questionDao));
        controllers.put("/users/create", new CreateUserController());
        controllers.put("/users/form", new ForwardController("/user/form.jsp"));
        controllers.put("/users", new ListUserController());
        controllers.put("/users/loginForm", new ForwardController("/user/login.jsp"));
        controllers.put("/users/login", new LoginController());
        controllers.put("/users/profile", new ProfileController());
        controllers.put("/users/logout", new LogoutController());
        controllers.put("/users/updateForm", new UpdateUserFormController());
        controllers.put("/users/update", new UpdateUserController());

        controllers.put("/qna/show", new ShowController(answerDao, questionDao));
        controllers.put("/api/qna/addAnswer", new AddAnswerController(answerDao, questionDao));
        controllers.put("/api/qna/deleteAnswer", new DeleteAnswerController(answerDao));
        controllers.put("/qna/form", new CreateFormQuestionController());
        controllers.put("/qna/create", new CreateQuestionController(questionDao));
        controllers.put("/qna/updateForm", new UpdateFormQuestionController(questionDao));
        controllers.put("/qna/update", new UpdateQuestionController(questionDao));
        controllers.put("/qna/delete", new DeleteQuestionController(qnaService));
        controllers.put("/api/qna/deleteQuestion", new ApiDeleteQuestionController(qnaService));
    }

    @Override
    public Object getHandler(HttpServletRequest request) {
        return controllers.get(request.getRequestURI());
    }
}
